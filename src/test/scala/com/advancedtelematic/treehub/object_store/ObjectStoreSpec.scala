package com.advancedtelematic.treehub.object_store

import java.nio.file.Files

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.advancedtelematic.data.DataType.{ObjectId, TObject}
import com.advancedtelematic.treehub.db.ObjectRepositorySupport
import com.advancedtelematic.util.{DatabaseSpec, TreeHubSpec}

import scala.concurrent.ExecutionContext

class ObjectStoreSpec extends TreeHubSpec with DatabaseSpec with ObjectRepositorySupport {

  implicit val ec = ExecutionContext.global
  implicit val system = ActorSystem("ObjectStoreSpecSystem")
  implicit val mat = ActorMaterializer()

  val localStorage = new LocalFsBlobStore(Files.createTempDirectory("treehub").toFile)

  val objectStore = new ObjectStore(localStorage)

  test("saves object to database and blob store") {
    val tobj = TObject(defaultNs, ObjectId("wat"))
    val blob = ByteString("some text")
    val res = objectStore.store(defaultNs, tobj.id, Source.single(blob)).futureValue

    res shouldBe tobj
    objectRepository.exists(tobj.namespace, tobj.id).futureValue shouldBe true
    localStorage.exists(tobj.namespace, tobj.id).futureValue shouldBe true
  }
}
