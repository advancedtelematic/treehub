package com.advancedtelematic.treehub.object_store

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.advancedtelematic.data.DataType.{ObjectId, TObject}
import com.advancedtelematic.treehub.db.ObjectRepositorySupport
import com.advancedtelematic.treehub.http.Errors
import org.genivi.sota.data.Namespace
import slick.driver.MySQLDriver.api._

import scala.concurrent.{ExecutionContext, Future}

class ObjectStore(blobStore: BlobStore)(implicit ec: ExecutionContext, db: Database) extends ObjectRepositorySupport {
  def store(namespace: Namespace, id: ObjectId, blob: Source[ByteString, _]): Future[TObject] = {
    val tobj = TObject(namespace, id)

    for {
      _ <- ensureNotExists(namespace, id)
      _ <- blobStore.store(namespace, id, blob)
      _ <- objectRepository.create(tobj)
    } yield tobj
  }

  def exists(namespace: Namespace, id: ObjectId): Future[Boolean] =
    for {
      dbExists <- objectRepository.exists(namespace, id)
      fsExists <- blobStore.exists(namespace, id)
    } yield fsExists && dbExists

  def findBlob(namespace: Namespace, id: ObjectId): Future[Source[ByteString, _]] = {
    ensureExists(namespace, id).flatMap(_ => blobStore.find(namespace, id))
  }

  def readFull(namespace: Namespace, id: ObjectId): Future[ByteString] = {
    blobStore.readFull(namespace, id)
  }

  def usage(namespace: Namespace): Future[Long] = blobStore.usage(namespace)

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
