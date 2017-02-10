package com.advancedtelematic.treehub.object_store

import akka.http.scaladsl.model.{HttpEntity, HttpResponse, MediaTypes, StatusCodes}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.advancedtelematic.data.DataType.ObjectId
import org.genivi.sota.data.Namespace

import scala.concurrent.Future
import scala.util.control.NoStackTrace

trait BlobStore {
  def store(namespace: Namespace, id: ObjectId, blob: Source[ByteString, _]): Future[Long]

  def buildResponse(namespace: Namespace, id: ObjectId, clientAcceptsRedirects: Boolean = false): Future[HttpResponse]

  def readFull(namespace: Namespace, id: ObjectId): Future[ByteString]

  def exists(namespace: Namespace, id: ObjectId): Future[Boolean]

  protected def buildResponseFromBytes(source: Source[ByteString, _]): HttpResponse = {
    val entity = HttpEntity(MediaTypes.`application/octet-stream`, source)
    HttpResponse(StatusCodes.OK, entity = entity)
  }
}

case class BlobStoreError(msg: String, cause: Throwable = null) extends Throwable(msg, cause) with NoStackTrace

