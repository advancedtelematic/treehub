package com.advancedtelematic.treehub.object_store

import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.advancedtelematic.data.DataType.{ObjectId, TObject}
import com.advancedtelematic.treehub.db.ObjectRepositorySupport
import com.advancedtelematic.util.{DatabaseSpec, TreeHubSpec}
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.ExecutionContext
import com.advancedtelematic.treehub.http.Errors
import org.genivi.sota.data.Namespace

class ObjectStoreSpec extends TreeHubSpec with DatabaseSpec with ObjectRepositorySupport with PatienceConfiguration {

  implicit val ec = ExecutionContext.global
  implicit val system = ActorSystem("ObjectStoreSpecSystem")
  implicit val mat = ActorMaterializer()

  override implicit def patienceConfig = PatienceConfig(timeout = Span(3, Seconds), interval = Span(500, Millis))

  val localStorageDir = Files.createTempDirectory("treehub").toFile

  val localStorage = new LocalFsBlobStore(localStorageDir)

  val objectStore = new ObjectStore(localStorage)

  test("saves object to database and blob store") {
    val blob = ByteString("some text")
    val tobj = TObject(defaultNs, ObjectId("wat"), blob.length)
    val res = objectStore.store(defaultNs, tobj.id, Source.single(blob)).futureValue

    res shouldBe tobj
    objectRepository.exists(tobj.namespace, tobj.id).futureValue shouldBe true
    localStorage.exists(tobj.namespace, tobj.id).futureValue shouldBe true
  }

  test("findBlob fails if object is not found") {
    val tobj = TObject(defaultNs, ObjectId("otherid"), 0L)

    whenReady(objectRepository.create(tobj)) { _ =>
      objectStore.findBlob(tobj.namespace, tobj.id).failed.futureValue shouldBe Errors.ObjectNotFound
    }
  }

  test("usage returns sum of usage for specified namespace only") {
    val ns = Namespace("usage-ns")

    val f = for {
       _ <- objectRepository.create(TObject(ns, ObjectId("otherid-1"), 200L))
       _ ← objectRepository.create(TObject(ns, ObjectId("otherid-2"), 100L))
       usage ← objectStore.usage(ns)
    } yield usage

    f.futureValue shouldBe 300L
  }

  test("usage forces usage update if usage is 0L for at least one object") {
    val ns = Namespace("usage-outdated-ns")

    val obj1 = TObject(ns, ObjectId("7ba6c2ff5ee68bbf528e3c4d5d227dcc40688c38155e114ea980749a08b45191.commit"), 200L)
    val obj2 = TObject(ns, ObjectId("15a432bcff53161f008974b476406b2a102c7862aef1fd9b63c27cb0133ac9d1.commit"), 0L)

    val text = "This is object"

    val nsDir = Files.createDirectories(Paths.get(localStorageDir.toString, ns.get, "15"))
    Files.write(Paths.get(nsDir.toString, "a432bcff53161f008974b476406b2a102c7862aef1fd9b63c27cb0133ac9d1.commit"), text.toCharArray.map(_.toByte))

    val f = for {
       _ <- objectRepository.create(obj1)
       _ ← objectRepository.create(obj2)
       usage ← objectStore.usage(ns)
    } yield usage

    f.futureValue shouldBe (200L + text.length)
  }
}
