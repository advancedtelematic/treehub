package com.advancedtelematic.treehub.object_store

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.advancedtelematic.data.DataType.{ObjectId, TObject}
import com.advancedtelematic.treehub.db.{ObjectRepositorySupport, StorageUsageStateUpdateSupport}
import com.advancedtelematic.treehub.http.Errors
import org.genivi.sota.data.Namespace
import slick.driver.MySQLDriver.api._

import scala.concurrent.{ExecutionContext, Future}

class ObjectStore(blobStore: BlobStore)(implicit ec: ExecutionContext, db: Database) extends ObjectRepositorySupport
  with StorageUsageStateUpdateSupport {

  def store(namespace: Namespace, id: ObjectId, blob: Source[ByteString, _]): Future[TObject] = {
    for {
      _ <- ensureNotExists(namespace, id)
      size <- blobStore.store(namespace, id, blob)
      tobj = TObject(namespace, id, size)
      _ <- objectRepository.create(tobj)
    } yield tobj
  }

  def exists(namespace: Namespace, id: ObjectId): Future[Boolean] =
    for {
      dbExists <- objectRepository.exists(namespace, id)
      fsExists <- blobStore.exists(namespace, id)
    } yield fsExists && dbExists

  def findBlob(namespace: Namespace, id: ObjectId): Future[(Long, Source[ByteString, _])] = {
    for {
      _ <- ensureExists(namespace, id)
      tobj <- objectRepository.find(namespace, id)
      bytes <- blobStore.find(namespace, id)
    } yield (tobj.byteSize, bytes)
  }

  def readFull(namespace: Namespace, id: ObjectId): Future[ByteString] = {
    blobStore.readFull(namespace, id)
  }

  def usage(namespace: Namespace): Future[Long] = {
    storageUsageState.isOutdated(namespace).flatMap { isOutdated =>
      if (isOutdated)
        blobStore.usage(namespace)
          .flatMap(usage => storageUsageState.update(namespace, usage))
          .flatMap(_ => objectRepository.usage(namespace))
      else
        objectRepository.usage(namespace)
    }
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
