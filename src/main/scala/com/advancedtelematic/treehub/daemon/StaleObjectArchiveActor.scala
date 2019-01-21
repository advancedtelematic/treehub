package com.advancedtelematic.treehub.daemon

import java.time.Instant

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, Props}
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
  sealed trait Msg
  case object Tick
  case class Done(count: Long)

  val expiresAfter = java.time.Duration.ofHours(1)

  def props(blobStore: BlobStore)(implicit db: Database, mat: Materializer) = Props(new StaleObjectArchiveActor(blobStore))
}

class StaleObjectArchiveActor(blobStore: BlobStore)(implicit db: Database, mat: Materializer) extends Actor
  with ObjectRepositorySupport
  with ArchivedObjectRepositorySupport
  with ActorLogging {

  import akka.pattern.pipe
  import context.dispatcher

  override def preStart(): Unit = { // TODO:SM Best to just use postRestart?
    self ! Tick
  }

  import scala.async.Async._

  def isExpired(createdAt: Instant): Boolean = {
    createdAt.isBefore(Instant.now().minus(StaleObjectArchiveActor.expiresAfter))
  }

  def handle(obj: TObject, createdAt: Instant): Future[ObjectId] = async {
    val exists = await(blobStore.exists(obj.namespace, obj.id))

    if(exists) {
      await(objectRepository.setCompleted(obj.namespace, obj.id))
      obj.id
    } else if(isExpired(createdAt)) {
      log.info(s"Object ${obj.id} is stale, client did not finish upload within ${StaleObjectArchiveActor.expiresAfter}, archiving")
      await(archivedObjectRepository.archive(obj, createdAt, s"Expired, client did not upload within ${StaleObjectArchiveActor.expiresAfter}"))
      obj.id
    } else {
      log.debug(s"No change needed for object ${obj.id}, did not expire yet")
      obj.id
    }
  }

  val logObjectProcessed = Flow[ObjectId].map { id =>
    log.info(s"Processed object $id")
    id
  }

  override def receive: Receive = {
    case Done(count) =>
      if(count > 0)
        log.info(s"Batch done, processed $count objects")
      else
        log.debug("Tick, scheduling next execution")

      context.system.scheduler.scheduleOnce(5.seconds, self, Tick)

    case Failure(ex) =>
      throw ex // TODO:SM This caused infinite restart because it schedules Tick right after, which still fails

    case Tick =>
      val source = objectRepository.findAllByStatus(ObjectStatus.CLIENT_UPLOADING)

      val sink = Sink.fold[Long, ObjectId](0) { (count, _) => count + 1 }

      source.mapAsyncUnordered(3)((handle _).tupled).via(logObjectProcessed).runWith(sink).map(Done.apply).pipeTo(self) // TODO:SM best use sender() to test?
  }
}
