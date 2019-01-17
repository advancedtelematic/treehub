package com.advancedtelematic.treehub.object_store

import java.io.File

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import com.advancedtelematic.data.DataType.{ObjectId, ObjectIdOps, ObjectStatus, TObject}
import com.advancedtelematic.util.TreeHubSpec
import org.scalatest.time.{Seconds, Span}

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext
import cats.syntax.either._

class S3BlobStoreIntegrationSpec extends TreeHubSpec {
  implicit val ec = ExecutionContext.global

  implicit lazy val system = ActorSystem("S3BlobStoreSpec")

  implicit lazy val mat = ActorMaterializer()

  val s3BlobStore = new S3BlobStore(s3Credentials, true)

  override implicit def patienceConfig = PatienceConfig().copy(timeout = Span(15, Seconds))

  test("can store object")  {
    val tobj = TObject(defaultNs, ObjectId.parse("ce720e82a727efa4b30a6ab73cefe31a8d4ec6c0d197d721f07605913d2a279a.commit").toOption.get, 0L, ObjectStatus.UPLOADED)
    val blob = ByteString("this is byte.")

    val source = Source.single(blob)

    val size = s3BlobStore.store(defaultNs, tobj.id, source).futureValue

    size shouldBe 13
  }

  test("can retrieve big object") {
    val tobj = TObject(defaultNs, ObjectId.parse("ce720e82a727efa4b30a6ab73cefe31a8d4ec6c0d197d721f07605913d2a279a.commit").toOption.get, 0L, ObjectStatus.UPLOADED)
    val blob = ByteString("this is byte. Call me. maybe.")

    val source = Source.single(blob)

    val f = async {
      await(s3BlobStore.store(defaultNs, tobj.id, source))
      await(s3BlobStore.readFull(tobj.namespace, tobj.id))
    }

    f.futureValue.utf8String shouldBe "this is byte. Call me. maybe."
    s3BlobStore.exists(tobj.namespace, tobj.id).futureValue shouldBe true
  }


  test("XXX can retrieve an object") {
    val file = new File(this.getClass.getResource(s"/blobs/myfile.bin").getFile)
    val source = FileIO.fromPath(file.toPath)
    val tobj = TObject(defaultNs, ObjectId.parse("625e61876cbe98fbf164c8ce5975c6d69a4ba0e9fa57c729ea06d02fd966a9cc.file").toOption.get, 0L, ObjectStatus.UPLOADED)

    val f = async {
      await(s3BlobStore.store(defaultNs, tobj.id, source))

      println("upload finished")

      await(s3BlobStore.readFull(tobj.namespace, tobj.id))
    }

    f.futureValue.size shouldBe file.length()
    s3BlobStore.exists(tobj.namespace, tobj.id).futureValue shouldBe true
  }

  test("build response builds a redirect") {
    val tobj = TObject(defaultNs, ObjectId.parse("ce720e82a727efa4b30a6ab73cefe31a8d4ec6c0d197d721f07605913d2a279a.commit").toOption.get, 0L, ObjectStatus.UPLOADED)
    val blob = ByteString("this is byte. Call me. maybe.")
    val source = Source.single(blob)

    val response = async {
      await(s3BlobStore.store(defaultNs, tobj.id, source))
      await(s3BlobStore.buildResponse(tobj.namespace, tobj.id))
    }.futureValue

    response.status shouldBe StatusCodes.Found
    response.headers.find(_.is("location")).get.value() should include(tobj.id.filename.toString)
  }

  test("build response a response containing the object content") {
    pending

    val tobj = TObject(defaultNs, ObjectId.parse("ce720e82a727efa4b30a6ab73cefe31a8d4ec6c0d197d721f07605913d2a279a.commit").toOption.get, 0L, ObjectStatus.UPLOADED)
    val blob = ByteString("this is byte. Call me. maybe.")
    val source = Source.single(blob)

    val response = async {
      await(s3BlobStore.store(defaultNs, tobj.id, source))
      await(s3BlobStore.buildResponse(tobj.namespace, tobj.id))
    }.futureValue

    response.status shouldBe StatusCodes.OK
    response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).futureValue.utf8String shouldBe "this is byte. Call me. maybe."
  }
}
