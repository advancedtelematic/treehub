package com.advancedtelematic.treehub.object_store

import akka.http.scaladsl.model.HttpResponse
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.advancedtelematic.data.DataType.{ObjectId, TObject}
import com.advancedtelematic.libats.data.Namespace
import com.advancedtelematic.treehub.db.ObjectRepositorySupport
import com.advancedtelematic.treehub.http.Errors
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class ObjectStore(blobStore: BlobStore)(implicit ec: ExecutionContext, db: Database) extends ObjectRepositorySupport {

  def store(namespace: Namespace, id: ObjectId, blob: Source[ByteString, _]): Future[TObject] = for {
    size <- blobStore.store(namespace, id, blob)
    obj <- objectRepository.create(TObject(namespace, id, size))
  } yield obj

  def exists(namespace: Namespace, id: ObjectId): Future[Boolean] =
    for {
      dbExists <- objectRepository.exists(namespace, id)
      fsExists <- blobStore.exists(namespace, id)
    } yield fsExists && dbExists

  def findBlob(namespace: Namespace, id: ObjectId): Future[(Long, HttpResponse)] = {
    for {
      _ <- ensureExists(namespace, id)
      tobj <- objectRepository.find(namespace, id)
      response <- blobStore.buildResponse(namespace, id)
    } yield (tobj.byteSize, response)
  }

  def readFull(namespace: Namespace, id: ObjectId): Future[ByteString] = {
    blobStore.readFull(namespace, id)
  }

  def usage(namespace: Namespace): Future[Long] = {
    objectRepository.usage(namespace)
  }

  private def ensureExists(namespace: Namespace, id: ObjectId): Future[ObjectId] = {
    exists(namespace, id).flatMap {
      case true => Future.successful(id)
      case false => Future.failed(Errors.ObjectNotFound)
    }
  }

  private def ensureNotExists(namespace: Namespace, id: ObjectId): Future[ObjectId] = {
    exists(namespace: Namespace, id).flatMap {
      case true => Future.failed(Errors.ObjectExists)
      case false => Future.successful(id)
    }
  }
}
