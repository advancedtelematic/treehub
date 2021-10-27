package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.advancedtelematic.data.DataType.ObjectId
import com.advancedtelematic.libats.data.DataType
import com.advancedtelematic.treehub.object_store.{BlobStore, ObjectStore}
import com.advancedtelematic.util.ResourceSpec.ClientTObject
import com.advancedtelematic.util.{ResourceSpec, TreeHubSpec}
import com.amazonaws.SdkClientException

import scala.concurrent.Future

class TreehubRoutesSpec extends TreeHubSpec with ResourceSpec {

  val errorBlobStore = new BlobStore {
    override def storeStream(namespace: DataType.Namespace, id: ObjectId, size: Long, blob: Source[ByteString, _]): Future[Long] = ???

    override val supportsOutOfBandStorage: Boolean = false

    override def storeOutOfBand(namespace: DataType.Namespace, id: ObjectId): Future[BlobStore.OutOfBandStoreResult] = ???

    override def buildResponse(namespace: DataType.Namespace, id: ObjectId): Future[HttpResponse] = ???

    override def readFull(namespace: DataType.Namespace, id: ObjectId): Future[ByteString] = ???

    override def exists(namespace: DataType.Namespace, id: ObjectId): Future[Boolean] = FastFuture.failed(new SdkClientException("Timeout on waiting"))

    override def deleteByNamespace(namespace: DataType.Namespace): Future[Unit] = ???
  }

  val errorObjectStore = new ObjectStore(errorBlobStore)

  override lazy val routes = new TreeHubRoutes(
    namespaceExtractor, namespaceExtractor, messageBus, errorObjectStore, deltaStore, fakeUsageUpdate).routes

  test("returns 408 when an aws.SdkClientException is thrown") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.RequestTimeout
    }
  }
}
