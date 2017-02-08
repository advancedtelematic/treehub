package com.advancedtelematic.treehub.db

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Status, SupervisorStrategy}
import akka.pattern.{ask, pipe}
import akka.routing.RandomPool
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import com.advancedtelematic.data.DataType.ObjectId
import com.advancedtelematic.treehub.Settings
import com.advancedtelematic.treehub.db.Msg._
import com.advancedtelematic.treehub.object_store.{LocalFsBlobStore, S3BlobStore}
import org.genivi.sota.data.Namespace
import org.genivi.sota.db.DatabaseConfig
import org.slf4j.LoggerFactory
import slick.driver.MySQLDriver.api._

import scala.async.Async._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object Msg {
  sealed trait MigrationResult

  case object Migrated extends MigrationResult
  case class MigrationError(msg: String) extends MigrationResult
  case object LocalMissing extends MigrationResult
  case object S3Exists extends MigrationResult

  case class MigrationItem(ns: Namespace, id: ObjectId)

  object Start
  case class PendingWork(work: Seq[MigrationItem])
}

object LocalToS3Migration extends App with Settings with DatabaseConfig {
  implicit val _db = db
  implicit val system = ActorSystem("LocalToS3Migration")
  import system.dispatcher
  implicit val mat = ActorMaterializer()
  val log = LoggerFactory.getLogger(this.getClass)

  val localFsBlobStore = LocalFsBlobStore(localStorePath)

  val s3BlobStore = new S3BlobStore(s3Credentials)

  val migrationF = new LocalToS3Migration(localFsBlobStore, s3BlobStore).migrate()

  val res = Await.result(migrationF, Duration.Inf)

  log.info("Migration finished")
  log.info("Total pending: {}", res.size)

  val resultMap = res.foldLeft(Map.empty[String, Long].withDefaultValue(0l)) { (acc, item) =>
    item match {
      case Migrated => acc + ("Migrated" -> (acc("Migrated") + 1l))
      case MigrationError(_) => acc + ("Error" -> (acc("Error") + 1l))
      case LocalMissing => acc + ("LocalMissing" -> (acc("LocalMissing") + 1l))
      case S3Exists => acc + ("S3Exists" -> (acc("S3Exists") + 1l))
    }
  }

  log.info(resultMap.mkString("\n"))

  system.terminate()
}

class S3Migrationleader(localFsBlobStore: LocalFsBlobStore, s3BlobStore: S3BlobStore)
                       (implicit val db: Database, mat: Materializer) extends Actor with ActorLogging {

  import context.dispatcher

  val WORKER_COUNT = 128

  private val router = {
    val routerProps = RandomPool(WORKER_COUNT)
      .withSupervisorStrategy(SupervisorStrategy.defaultStrategy)
      .props(Props(new S3MigrationWorker(localFsBlobStore, s3BlobStore)))

    context.system.actorOf(routerProps)
  }

  var results = Vector.empty[MigrationResult]
  var waitingForCount: Long = 0
  var receiver = context.parent

  val pendingMigrationsQueryIO =
    sql"""select namespace, object_id from localtos3_migration where status = 'Pending' ORDER BY namespace LIMIT 120""".as[(String, String)]

  def receivedWorkResult = {
    if(results.size >= waitingForCount)
      fetchPending
  }

  def fetchPending = async {
    val pendingDB = await(db.run(pendingMigrationsQueryIO))

    val pending = pendingDB.map { case (namespaceS, objectIdS) =>
      val objectId = ObjectId.parse(objectIdS).valueOr(err => throw new IllegalArgumentException(err))
      MigrationItem(Namespace(namespaceS), objectId)
    }

    PendingWork(pending)
  }.pipeTo(self)

  override def receive: Receive = {
    case Start =>
      receiver = sender()
      fetchPending

    case PendingWork(work) =>
      if(work.isEmpty) {
        receiver ! results
      } else {
        work.foreach(router ! _)
        waitingForCount += work.size
      }

    case res: MigrationResult =>
      results = results :+ res
      receivedWorkResult

    case Status.Failure(err) =>
      log.error(err, "Failed migration")
      results = results :+ MigrationError(err.getMessage)
      receivedWorkResult
  }
}


class S3MigrationWorker(localFsBlobStore: LocalFsBlobStore, s3BlobStore: S3BlobStore)
                       (implicit val db: Database, mat: Materializer) extends Actor with ActorLogging {

  import context.dispatcher
  import org.genivi.sota.db.SlickExtensions._
  import org.genivi.sota.refined.SlickRefined._

  def updateStatus(result: MigrationResult, namespace: Namespace, id: ObjectId): Future[MigrationResult] =
    db.run(updateStatusAction(result, namespace, id))

  def migrateItem(namespace: Namespace, objectId: ObjectId): Future[MigrationResult] = {
    async {
      val localExists = await(localFsBlobStore.exists(namespace, objectId))

      val result = if (!localExists) {
        await(updateStatus(LocalMissing, namespace, objectId))
      } else if(await(s3BlobStore.exists(namespace, objectId))) {
        await(updateStatus(S3Exists, namespace, objectId))
      } else {
        val bytes = await(localFsBlobStore.buildResponse(namespace, objectId)).entity.dataBytes
        val size = await(s3BlobStore.store(namespace, objectId, bytes))

        val updateObjIO = Schema.objects
          .filter(_.namespace === namespace).filter(_.id === objectId)
          .map(_.size)
          .update(size)

        val updateMigratedIO = updateStatusAction(Migrated, namespace, objectId)

        await(db.run(DBIO.seq(updateObjIO, updateMigratedIO).transactionally))
        Migrated
      }

      log.info(s"${namespace.get}, ${objectId.get} -> $result")

      result
    }
  }

  override def receive: Receive = {
    case MigrationItem(namespace, objectId) =>
      migrateItem(namespace, objectId).pipeTo(sender())
  }

  def updateStatusAction(result: MigrationResult, namespace: Namespace, objectId: ObjectId): DBIO[MigrationResult] =
    sqlu"""update localtos3_migration set status = '#$result' where namespace = '#${namespace.get}' and object_id = '#${objectId.get}'""".map(_ => result)
}

class LocalToS3Migration(localFsBlobStore: LocalFsBlobStore, s3BlobStore: S3BlobStore)
                        (implicit val db: Database, system: ActorSystem, mat: Materializer) {

  import system.dispatcher
  val log = LoggerFactory.getLogger(this.getClass)

  def migrate(): Future[Seq[MigrationResult]] = {
    log.info("Starting migration")

    val leader = system.actorOf(Props(new S3Migrationleader(localFsBlobStore, s3BlobStore)))

    async {
      await(db.run(createMigrationTblIO))

      implicit val t = Timeout(1, TimeUnit.DAYS)

      await((leader ? Start).mapTo[Seq[MigrationResult]])
    }
  }

  val createMigrationTblIO =
    sqlu"""CREATE TABLE IF NOT EXISTS localtos3_migration
            (status VARCHAR(255) NULL, PRIMARY KEY (namespace, object_id))
           as
           SELECT namespace, object_id, 'Pending' as status, size as old_size from `object`
         """
}
