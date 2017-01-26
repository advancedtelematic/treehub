package com.advancedtelematic.treehub.object_store

import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.advancedtelematic.data.DataType.{ObjectId, TObject}
import com.advancedtelematic.treehub.db.ObjectRepositorySupport
import com.advancedtelematic.util.TreeHubSpec
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.ExecutionContext
import com.advancedtelematic.treehub.http.Errors
import org.genivi.sota.core.DatabaseSpec
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
    val tobj = TObject(defaultNs, ObjectId.parse("48eaf23f5ae202229c3f16a74f1c537efb5cae708aba89d1914b7cb13aec173e.filez").toOption.get, blob.length)
    val res = objectStore.store(defaultNs, tobj.id, Source.single(blob)).futureValue

    res shouldBe tobj
    objectRepository.exists(tobj.namespace, tobj.id).futureValue shouldBe true
    localStorage.exists(tobj.namespace, tobj.id).futureValue shouldBe true
  }

  test("findBlob fails if object is not found") {
    val tobj = TObject(defaultNs, ObjectId.parse("ce720e82a727efa4b30a6ab73cefe31a8d4ec6c0d197d721f07605913d2a279a.commit").toOption.get, 0L)

    whenReady(objectRepository.create(tobj)) { _ =>
      objectStore.findBlob(tobj.namespace, tobj.id).failed.futureValue shouldBe Errors.ObjectNotFound
    }
  }

  test("usage returns sum of usage for specified namespace only") {
    val ns = Namespace("usage-ns")

    val f = for {
       _ <- objectRepository.create(TObject(ns, ObjectId.parse("28417941a93c09bd13e220c0b0517fc1464428be16c0bc342187742d11285ca1.dirtree").toOption.get, 200L))
       _ <- objectRepository.create(TObject(ns, ObjectId.parse("902030f091a1e6a790517d62055127cb61c553fb6fe8682d7c2d0d6ef1941891.dirtree").toOption.get, 100L))
       usage <- objectStore.usage(ns)
    } yield usage

    f.futureValue shouldBe 300L
  }

  test("usage forces usage update if usage is 0L for at least one object") {
    val ns = Namespace("usage-outdated-ns")

    val obj1 = TObject(ns, ObjectId.parse("7ba6c2ff5ee68bbf528e3c4d5d227dcc40688c38155e114ea980749a08b45191.commit").toOption.get, 200L)
    val obj2 = TObject(ns, ObjectId.parse("0016d23b9fcc8edf08ebfc74fa6abf589c3f555ae7845d6a57df7c13ce32fe64.filez").toOption.get, 0L)

    val text = "This is object"

    val nsDir = Files.createDirectories(Paths.get(localStorageDir.toString, ns.get))
    val objPath = ObjectId.path(nsDir, obj2.id)
    Files.createDirectories(objPath.getParent)
    Files.write(objPath, text.toCharArray.map(_.toByte))

    val f = for {
       _ <- objectRepository.create(obj1)
       _ <- objectRepository.create(obj2)
       usage <- objectStore.usage(ns)
    } yield usage

    f.futureValue shouldBe (200L + text.length)
  }

  test("usage forces usage update on outdated (usage=0) rows only ") {
    val ns = Namespace("usage-outdated-single-ns")

    val obj1 = TObject(ns, ObjectId.parse("7ba6c2ff5ee68bbf528e3c4d5d227dcc40688c38155e114ea980749a08b45191.commit").toOption.get, 200L)
    val obj2 = TObject(ns, ObjectId.parse("0016d23b9fcc8edf08ebfc74fa6abf589c3f555ae7845d6a57df7c13ce32fe64.filez").toOption.get, 0L)

    val text = "This is object"

    val nsDir = Files.createDirectories(Paths.get(localStorageDir.toString, ns.get))

    val objPath1 = ObjectId.path(nsDir, obj1.id)
    Files.createDirectories(objPath1.getParent)
    Files.write(objPath1, text.toCharArray.map(_.toByte))

    val objPath2 = ObjectId.path(nsDir, obj2.id)
    Files.createDirectories(objPath2.getParent)
    Files.write(objPath2, text.toCharArray.map(_.toByte))

    val f = for {
      _ <- objectRepository.create(obj1)
      _ <- objectRepository.create(obj2)
      usage <- objectStore.usage(ns)
    } yield usage

    f.futureValue shouldBe (200L + text.length)
  }
}
