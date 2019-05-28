package com.advancedtelematic.treehub.daemon

import java.nio.file.Files
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKitBase}
import com.advancedtelematic.data.DataType.{ObjectStatus, TObject}
import com.advancedtelematic.libats.test.{DatabaseSpec, LongTest}
import com.advancedtelematic.treehub.daemon.StaleObjectArchiveActor.{Done, Tick}
import com.advancedtelematic.treehub.db.{ArchivedObjectRepositorySupport, ObjectRepositorySupport}
import com.advancedtelematic.treehub.http.Errors
import com.advancedtelematic.treehub.object_store.{LocalFsBlobStore, S3BlobStore}
import com.advancedtelematic.util.ResourceSpec.ClientTObject
import com.advancedtelematic.util.TreeHubSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import slick.jdbc.MySQLProfile.api._

protected abstract class StaleObjectArchiveActorSpecUtil extends TreeHubSpec with TestKitBase with ImplicitSender with DatabaseSpec
  with ArchivedObjectRepositorySupport
  with ObjectRepositorySupport with Eventually with LongTest with BeforeAndAfterEach {

  override implicit lazy val system: ActorSystem = ActorSystem(this.getClass.getSimpleName)

  implicit lazy val mat = ActorMaterializer()

  val subject: ActorRef

  override def beforeEach() = {
    db.run(sqlu"delete from `object`").futureValue
  }

  override def afterAll(): Unit = {
    subject ! PoisonPill
  }
}

class StaleObjectArchiveActorSpec extends StaleObjectArchiveActorSpecUtil {
  import system.dispatcher

  lazy val storage = new LocalFsBlobStore(Files.createTempDirectory("stale-objects-store"))

  lazy val subject = system.actorOf(StaleObjectArchiveActor.withBackOff(storage, autoStart = false))

  test("archives expired objects") {
    val obj = new ClientTObject()
    val tobj = TObject(defaultNs, obj.objectId, obj.blob.size, ObjectStatus.CLIENT_UPLOADING)
    
    objectRepository.create(tobj).futureValue

    val expiredDt = Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS))

    db.run(sqlu"update `object` set created_at = $expiredDt where object_id = '#${obj.objectId.value}'").futureValue

    subject ! Tick
    val done = expectMsgType[Done]

    done.count shouldBe 1
    archivedObjectRepository.find(defaultNs, obj.objectId).futureValue.map(_._2) should contain(obj.objectId)
    objectRepository.find(defaultNs, obj.objectId).failed.futureValue shouldBe Errors.ObjectNotFound
  }

  test("does nothing to non stale objects that were not uploaded yet") {
    val obj = new ClientTObject()
    val tobj = TObject(defaultNs, obj.objectId, obj.blob.size, ObjectStatus.CLIENT_UPLOADING)

    objectRepository.create(tobj).futureValue

    subject ! Tick
    val done = expectMsgType[Done]

    done.count shouldBe 1
    objectRepository.find(defaultNs, obj.objectId).futureValue.status shouldBe ObjectStatus.CLIENT_UPLOADING
    archivedObjectRepository.find(defaultNs, obj.objectId).futureValue.map(_._2) shouldNot contain(obj.objectId)
  }

  test("does nothing to non CLIENT_UPLOADING objects") {
    val obj = new ClientTObject()
    val tobj = TObject(defaultNs, obj.objectId, obj.blob.size, ObjectStatus.UPLOADED)
    objectRepository.create(tobj).futureValue

    subject ! Tick
    val done = expectMsgType[Done]

    done.count shouldBe 0
    archivedObjectRepository.find(defaultNs, obj.objectId).futureValue.map(_._2) shouldNot contain(obj.objectId)
    objectRepository.find(defaultNs, obj.objectId).futureValue shouldBe tobj
  }

  test("marks uploaded objects as UPLOADED") {
    val obj = new ClientTObject()
    val tobj = TObject(defaultNs, obj.objectId, obj.blob.size, ObjectStatus.CLIENT_UPLOADING)

    objectRepository.create(tobj).futureValue
    storage.store(defaultNs, obj.objectId, obj.byteSource).futureValue

    subject ! Tick
    val done = expectMsgType[Done]

    done.count shouldBe 1
    objectRepository.find(defaultNs, obj.objectId).futureValue.status shouldBe ObjectStatus.UPLOADED
    archivedObjectRepository.find(defaultNs, obj.objectId).futureValue.map(_._2) shouldNot contain(obj.objectId)
  }

  test("schedules new run when receiving Done") {
    val obj = new ClientTObject()
    val tobj = TObject(defaultNs, obj.objectId, obj.blob.size, ObjectStatus.CLIENT_UPLOADING)

    subject ! Done(0)

    objectRepository.create(tobj).futureValue
    storage.store(defaultNs, obj.objectId, obj.byteSource).futureValue

    eventually({
      objectRepository.find(defaultNs, obj.objectId).futureValue.status shouldBe ObjectStatus.UPLOADED
      archivedObjectRepository.find(defaultNs, obj.objectId).futureValue.map(_._2) shouldNot contain(obj.objectId)
    })(patienceConfig.copy(timeout = Span(6, Seconds)), implicitly)
  }
}

class StaleObjectArchiveActorIntegrationSpec extends StaleObjectArchiveActorSpecUtil {
  import system.dispatcher

  val storage = S3BlobStore(s3Credentials, allowRedirects = false)
  lazy val subject = system.actorOf(StaleObjectArchiveActor.withBackOff(storage))

  test("marks uploaded objects as UPLOADED") {
    val obj = new ClientTObject()
    val tobj = TObject(defaultNs, obj.objectId, obj.blob.size, ObjectStatus.CLIENT_UPLOADING)

    objectRepository.create(tobj).futureValue
    storage.storeStream(defaultNs, obj.objectId, obj.blob.length, obj.byteSource).futureValue

    subject ! Tick

    val done = expectMsgType[Done]

    done.count shouldBe 1
    objectRepository.find(defaultNs, obj.objectId).futureValue.status shouldBe ObjectStatus.UPLOADED
    archivedObjectRepository.find(defaultNs, obj.objectId).futureValue.map(_._2) shouldNot contain(obj.objectId)
  }

  test("does nothing to non stale objects that were not uploaded yet") {
    val obj = new ClientTObject()
    val tobj = TObject(defaultNs, obj.objectId, obj.blob.size, ObjectStatus.CLIENT_UPLOADING)

    objectRepository.create(tobj).futureValue

    subject ! Tick
    val done = expectMsgType[Done]

    done.count shouldBe 1
    objectRepository.find(defaultNs, obj.objectId).futureValue.status shouldBe ObjectStatus.CLIENT_UPLOADING
    archivedObjectRepository.find(defaultNs, obj.objectId).futureValue.map(_._2) shouldNot contain(obj.objectId)
  }

  override def afterAll(): Unit = {
    shutdown(system)
  }
}
