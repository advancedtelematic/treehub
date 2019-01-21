package com.advancedtelematic.treehub.daemon

import java.nio.file.Files

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKitBase
import com.advancedtelematic.data.DataType.{ObjectStatus, TObject}
import com.advancedtelematic.libats.test.DatabaseSpec
import com.advancedtelematic.treehub.daemon.StaleObjectArchiveActor.Tick
import com.advancedtelematic.treehub.db.{ArchivedObjectRepositorySupport, ObjectRepositorySupport}
import com.advancedtelematic.treehub.object_store.LocalFsBlobStore
import com.advancedtelematic.util.ResourceSpec.ClientTObject
import com.advancedtelematic.util.TreeHubSpec
import org.scalatest.concurrent.Eventually

// TODO:SM Also write Integration spec which runs using s3

class StaleObjectArchiveSpec extends TreeHubSpec with TestKitBase with DatabaseSpec
  with ArchivedObjectRepositorySupport
  with ObjectRepositorySupport with Eventually {

  override implicit lazy val system: ActorSystem = ActorSystem(this.getClass.getSimpleName)

  implicit lazy val mat = ActorMaterializer()

  import system.dispatcher

  val storage = new LocalFsBlobStore(Files.createTempDirectory("stale-objects-store"))

  val subject = system.actorOf(StaleObjectArchiveActor.props(storage))

  test("archives stale objects ") (pending)

  test("does nothing to non stale objects")(pending)

  test("does nothing to non CLIENT_UPLOADING objects")(pending)

  test("marks uploaded objects as UPLOADED") {
    val obj = new ClientTObject()
    val tobj = TObject(defaultNs, obj.objectId, obj.blob.size, ObjectStatus.CLIENT_UPLOADING)

    objectRepository.create(tobj).futureValue
    storage.store(defaultNs, obj.objectId, obj.byteSource).futureValue

    // subject ! Tick

    eventually {
      archivedObjectRepository.find(defaultNs, obj.objectId).futureValue.map(_._2) shouldNot contain(obj.objectId)
    }
  }
}
