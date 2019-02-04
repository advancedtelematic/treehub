package com.advancedtelematic.treehub.daemon

import java.time.Instant

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.{Backoff, BackoffSupervisor}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}
import com.advancedtelematic.data.DataType.{ObjectId, ObjectStatus, TObject}
import com.advancedtelematic.treehub.daemon.StaleObjectArchiveActor.{Done, Tick}
import com.advancedtelematic.treehub.db.{ArchivedObjectRepositorySupport, ObjectRepositorySupport}
import com.advancedtelematic.treehub.object_store.BlobStore
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.Future
import scala.concurrent.duration._

object StaleObjectArchiveActor {
  case object Tick
  case class Done(count: Long)

  val defaultExpireDuration = java.time.Duration.ofHours(1)

  def props(blobStore: BlobStore, objectsExpireAfter: java.time.Duration = defaultExpireDuration, autoStart: Boolean = false)
           (implicit db: Database, mat: Materializer) =
    Props(new StaleObjectArchiveActor(blobStore, objectsExpireAfter, autoStart))

  def withBackOff(blobStore: BlobStore, objectsExpireAfter: java.time.Duration = defaultExpireDuration, autoStart: Boolean = false)
                 (implicit db: Database, mat: Materializer) =
    BackoffSupervisor.props(Backoff.onFailure(props(blobStore, objectsExpireAfter, autoStart), "stale-obj-worker", 5.seconds, 5.minutes, 0.25))
}

class StaleObjectArchiveActor(blobStore: BlobStore, objectsExpireAfter: java.time.Duration, autoStart: Boolean = false)(implicit db: Database, mat: Materializer) extends Actor
  with ObjectRepositorySupport
  with ArchivedObjectRepositorySupport
  with ActorLogging {

  import akka.pattern.pipe
  import context.dispatcher

  import scala.async.Async._

  private val tickInterval = 5.seconds

  override def preStart(): Unit = {
    if(autoStart)
      self ! Tick
  }

  def isExpired(createdAt: Instant): Boolean =
    createdAt.plus(objectsExpireAfter).isBefore(Instant.now)

  def completeOrArchive(obj: TObject, createdAt: Instant): Future[ObjectId] = async {
    val exists = await(blobStore.exists(obj.namespace, obj.id))

    if(exists) {
      log.info(s"Object ${obj.id} uploaded by client to remote server, marking as completed")
      await(objectRepository.setCompleted(obj.namespace, obj.id))
    } else if(isExpired(createdAt)) {
      log.info(s"Object ${obj.id} is stale, client did not finish upload within $objectsExpireAfter, archiving")
      await(archivedObjectRepository.archive(obj, createdAt, s"Expired, client did not upload within $objectsExpireAfter"))
    } else {
      log.debug(s"No change needed for object ${obj.id}, did not expire yet")
    }

    obj.id
  }

  def processAll: Future[Done] = {
    val source = objectRepository.findAllByStatus(ObjectStatus.CLIENT_UPLOADING)

    val logObjectProcessed = Flow[ObjectId].map { id =>
      log.info(s"Processed object $id")
      id
    }

    val sink = Sink.fold[Long, ObjectId](0) { (count, _) => count + 1 }

    source
      .mapAsyncUnordered(3)((completeOrArchive _).tupled)
      .via(logObjectProcessed)
      .runWith(sink).map(Done.apply)
  }

  override def receive: Receive = {
    case Done(count) =>
      if(count > 0)
        log.info(s"Batch done, processed $count objects")
      else
        log.debug("Tick, scheduling next execution")

      context.system.scheduler.scheduleOnce(tickInterval, self, Tick)

    case Failure(ex) =>
      throw ex

    case Tick =>
      processAll.pipeTo(sender())
  }
}
