package com.advancedtelematic.treehub.object_store

import java.nio.file.Files

import cats.syntax.either._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.advancedtelematic.data.DataType.{ObjectId, ObjectStatus, TObject}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.test.DatabaseSpec
import com.advancedtelematic.treehub.db.ObjectRepositorySupport
import com.advancedtelematic.util.TreeHubSpec
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.ExecutionContext
import com.advancedtelematic.treehub.http.Errors

class ObjectStoreSpec extends TreeHubSpec with DatabaseSpec with ObjectRepositorySupport with PatienceConfiguration {

  implicit val ec = ExecutionContext.global
  implicit val system = ActorSystem("ObjectStoreSpecSystem")
  implicit val mat = ActorMaterializer()

  override implicit def patienceConfig = PatienceConfig().copy(timeout = Span(3, Seconds))

  val localStorageDir = Files.createTempDirectory("treehub")

  val localStorage = new LocalFsBlobStore(localStorageDir)

  val objectStore = new ObjectStore(localStorage)

  test("saves object to database and blob store") {
    val blob = ByteString("some text")
    val tobj = TObject(defaultNs, ObjectId.parse("48eaf23f5ae202229c3f16a74f1c537efb5cae708aba89d1914b7cb13aec173e.filez").toOption.get, blob.length, ObjectStatus.UPLOADED)
    val res = objectStore.store(defaultNs, tobj.id, Source.single(blob)).futureValue

    res shouldBe tobj
    objectRepository.exists(tobj.namespace, tobj.id).futureValue shouldBe true
    localStorage.exists(tobj.namespace, tobj.id).futureValue shouldBe true
  }

  test("findBlob fails if object is not found") {
    val tobj = TObject(defaultNs, ObjectId.parse("ce720e82a727efa4b30a6ab73cefe31a8d4ec6c0d197d721f07605913d2a279a.commit").toOption.get, 0L, ObjectStatus.UPLOADED)

    whenReady(objectRepository.create(tobj)) { _ =>
      objectStore.findBlob(tobj.namespace, tobj.id).failed.futureValue shouldBe Errors.ObjectNotFound
    }
  }

  test("usage returns sum of usage for specified namespace only") {
    val ns = Namespace("usage-ns")

    val f = for {
       _ <- objectRepository.create(TObject(ns, ObjectId.parse("28417941a93c09bd13e220c0b0517fc1464428be16c0bc342187742d11285ca1.dirtree").toOption.get, 200L, ObjectStatus.UPLOADED))
       _ <- objectRepository.create(TObject(ns, ObjectId.parse("902030f091a1e6a790517d62055127cb61c553fb6fe8682d7c2d0d6ef1941891.dirtree").toOption.get, 100L, ObjectStatus.UPLOADED))
       usage <- objectStore.usage(ns)
    } yield usage

    f.futureValue shouldBe 300L
  }
}
