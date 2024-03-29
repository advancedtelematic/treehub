package com.advancedtelematic.treehub.object_store

import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.advancedtelematic.data.DataType.ObjectId
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.treehub.object_store.BlobStore.OutOfBandStoreResult

import scala.concurrent.Future
import scala.util.control.NoStackTrace

object BlobStore {
  sealed trait OutOfBandStoreResult
  case class UploadAt(uri: Uri) extends OutOfBandStoreResult
}

trait BlobStore {
  def storeStream(namespace: Namespace, id: ObjectId, size: Long, blob: Source[ByteString, _]): Future[Long]

  val supportsOutOfBandStorage: Boolean

  def storeOutOfBand(namespace: Namespace, id: ObjectId): Future[OutOfBandStoreResult]

  def buildResponse(namespace: Namespace, id: ObjectId): Future[HttpResponse]

  def readFull(namespace: Namespace, id: ObjectId): Future[ByteString]

  def exists(namespace: Namespace, id: ObjectId): Future[Boolean]

  def deleteByNamespace(namespace: Namespace): Future[Unit]

  protected def buildResponseFromBytes(source: Source[ByteString, _]): HttpResponse = {
    val entity = HttpEntity(MediaTypes.`application/octet-stream`, source)
    HttpResponse(StatusCodes.OK, entity = entity)
  }
}

case class BlobStoreError(msg: String, cause: Throwable = null) extends Throwable(msg, cause) with NoStackTrace

