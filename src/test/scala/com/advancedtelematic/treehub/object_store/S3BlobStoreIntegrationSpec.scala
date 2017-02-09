package com.advancedtelematic.treehub.object_store

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.advancedtelematic.data.DataType.{ObjectId, TObject}
import com.advancedtelematic.util.TreeHubSpec
import org.scalatest.time.{Seconds, Span}
import com.advancedtelematic.data.DataType.ObjectIdOps
import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext

class S3BlobStoreIntegrationSpec extends TreeHubSpec {
  implicit val ec = ExecutionContext.global

  implicit lazy val system = ActorSystem("S3BlobStoreSpec")

  implicit lazy val mat = ActorMaterializer()

  val s3BlobStore = new S3BlobStore(s3Credentials)

  override implicit def patienceConfig = PatienceConfig().copy(timeout = Span(3, Seconds))

  test("can store object")  {
    val tobj = TObject(defaultNs, ObjectId.parse("ce720e82a727efa4b30a6ab73cefe31a8d4ec6c0d197d721f07605913d2a279a.commit").toOption.get, 0L)

    val source = Source.single(ByteString("this is byte."))

    val size = s3BlobStore.store(defaultNs, tobj.id, source).futureValue

    size shouldBe 13
  }

  test("can retrieve an object") {
    val tobj = TObject(defaultNs, ObjectId.parse("ce720e82a727efa4b30a6ab73cefe31a8d4ec6c0d197d721f07605913d2a279a.commit").toOption.get, 0L)

    val source = Source.single(ByteString("this is byte. Call me. maybe."))

    val f = async {
      await(s3BlobStore.store(defaultNs, tobj.id, source))
      await(s3BlobStore.readFull(tobj.namespace, tobj.id))
    }

    f.futureValue.utf8String shouldBe "this is byte. Call me. maybe."
    s3BlobStore.exists(tobj.namespace, tobj.id).futureValue shouldBe true
  }

  test("build response builds a redirect") {
    pending // This is pending until we can change the client to follow these redirects

    val tobj = TObject(defaultNs, ObjectId.parse("ce720e82a727efa4b30a6ab73cefe31a8d4ec6c0d197d721f07605913d2a279a.commit").toOption.get, 0L)

    val source = Source.single(ByteString("this is byte. Call me. maybe."))

    val response = async {
      await(s3BlobStore.store(defaultNs, tobj.id, source))
      await(s3BlobStore.buildResponse(tobj.namespace, tobj.id))
    }.futureValue

    response.status shouldBe StatusCodes.Found
    response.headers.find(_.is("location")).get.value() should include(tobj.id.filename.toString)
  }

  test("build response a response containing the object redirect") {
    val tobj = TObject(defaultNs, ObjectId.parse("ce720e82a727efa4b30a6ab73cefe31a8d4ec6c0d197d721f07605913d2a279a.commit").toOption.get, 0L)

    val source = Source.single(ByteString("this is byte. Call me. maybe."))

    val response = async {
      await(s3BlobStore.store(defaultNs, tobj.id, source))
      await(s3BlobStore.buildResponse(tobj.namespace, tobj.id))
    }.futureValue

    response.status shouldBe StatusCodes.OK
    response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).futureValue.utf8String shouldBe "this is byte. Call me. maybe."
  }

}
