package com.advancedtelematic.treehub.object_store

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.advancedtelematic.data.DataType.{ObjectId, ObjectIdOps, ObjectStatus, TObject}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.util.ResourceSpec.ClientTObject
import com.advancedtelematic.util.TreeHubSpec
import org.scalatest.time.{Seconds, Span}

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext

class S3BlobStoreIntegrationSpec extends TreeHubSpec {
  implicit val ec = ExecutionContext.global

  implicit lazy val system = ActorSystem("S3BlobStoreSpec")

  implicit lazy val mat = ActorMaterializer()

  val ns = Namespace("S3BlobStoreIntegrationSpec")

  val s3BlobStore = new S3BlobStore(s3Credentials, false)

  override implicit def patienceConfig = PatienceConfig().copy(timeout = Span(15, Seconds))

  test("can store object")  {
    val tobj = TObject(ns, ObjectId.parse("ce720e82a727efa4b30a6ab73cefe31a8d4ec6c0d197d721f07605913d2a279a.commit").toOption.get, 0L, ObjectStatus.UPLOADED)
    val blob = ByteString("this is byte.")

    val source = Source.single(blob)

    val size = s3BlobStore.store(ns, tobj.id, source).futureValue

    size shouldBe 13
  }

  test("can retrieve an object") {
    val obj = new ClientTObject()

    val f = async {
      await(s3BlobStore.store(ns, obj.objectId, obj.byteSource))
      await(s3BlobStore.readFull(ns, obj.objectId))
    }

    f.futureValue.size shouldBe obj.blob.size
    s3BlobStore.exists(ns, obj.objectId).futureValue shouldBe true
  }

  test("build response builds a redirect") {
    val redirectS3BlobStore = new S3BlobStore(s3Credentials, true)

    val tobj = TObject(ns, ObjectId.parse("ce720e82a727efa4b30a6ab73cefe31a8d4ec6c0d197d721f07605913d2a279a.commit").toOption.get, 0L, ObjectStatus.UPLOADED)
    val blob = ByteString("this is byte. Call me. maybe.")
    val source = Source.single(blob)

    val response = async {
      await(redirectS3BlobStore.store(ns, tobj.id, source))
      await(redirectS3BlobStore.buildResponse(tobj.namespace, tobj.id))
    }.futureValue

    response.status shouldBe StatusCodes.Found
    response.headers.find(_.is("location")).get.value() should include(tobj.id.filename.toString)
  }

  test("build response a response containing the object content") {
    val tobj = TObject(ns, ObjectId.parse("ce720e82a727efa4b30a6ab73cefe31a8d4ec6c0d197d721f07605913d2a279a.commit").toOption.get, 0L, ObjectStatus.UPLOADED)
    val blob = ByteString("this is byte. Call me. maybe.")
    val source = Source.single(blob)

    val response = async {
      await(s3BlobStore.store(ns, tobj.id, source))
      await(s3BlobStore.buildResponse(tobj.namespace, tobj.id))
    }.futureValue

    response.status shouldBe StatusCodes.OK
    response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).futureValue.utf8String shouldBe "this is byte. Call me. maybe."
  }
}
