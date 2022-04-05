package com.advancedtelematic.treehub.object_store

import akka.actor.Scheduler

import java.io.File
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import com.advancedtelematic.data.DataType.{ObjectId, ObjectStatus, TObject}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.treehub.db.ObjectRepositorySupport
import com.advancedtelematic.treehub.http.Errors
import com.advancedtelematic.treehub.object_store.BlobStore.OutOfBandStoreResult
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class ObjectStore(blobStore: BlobStore)(implicit ec: ExecutionContext, db: Database, scheduler: Scheduler) extends ObjectRepositorySupport {

  import scala.async.Async._

  def completeClientUpload(namespace: Namespace, id: ObjectId): Future[Unit] =
    objectRepository.setCompleted(namespace, id).map(_ => ())

  def outOfBandStorageEnabled: Boolean = blobStore.supportsOutOfBandStorage

  def storeOutOfBand(namespace: Namespace, id: ObjectId, size: Long): Future[OutOfBandStoreResult] = {
    val obj = TObject(namespace, id, size, ObjectStatus.CLIENT_UPLOADING)
    lazy val createF = objectRepository.create(obj)

    lazy val uploadF =
      blobStore
        .storeOutOfBand(namespace, id)
        .recoverWith {
          case e =>
            objectRepository.delete(namespace, id)
              .flatMap(_ => FastFuture.failed(e))
              .recoverWith { case _ => FastFuture.failed(e) }
        }

    createF.flatMap(_ => uploadF)
  }

  def storeStream(namespace: Namespace, id: ObjectId, size: Long, blob: Source[ByteString, _]): Future[TObject] = {
    val obj = TObject(namespace, id, size, ObjectStatus.SERVER_UPLOADING)
    lazy val createF = objectRepository.create(obj)

    lazy val uploadF = async {
      val _size = await(blobStore.storeStream(namespace, id, size, blob))
      val newObj = obj.copy(byteSize = _size, status = ObjectStatus.UPLOADED)
      await(objectRepository.update(namespace, id, _size, ObjectStatus.UPLOADED))
      newObj
    }.recoverWith {
      case e =>
        objectRepository.delete(namespace, id)
          .flatMap(_ => FastFuture.failed(e))
          .recoverWith { case _ => FastFuture.failed(e) }
    }

    createF.flatMap(_ => uploadF)
  }

  def storeFile(namespace: Namespace, id: ObjectId, file: File): Future[TObject] = {
    val size = file.length()
    val source = FileIO.fromPath(file.toPath)
    storeStream(namespace, id, size, source)
  }

  def exists(namespace: Namespace, id: ObjectId): Future[Boolean] =
    for {
      dbExists <- objectRepository.exists(namespace, id)
      fsExists <- blobStore.exists(namespace, id)
    } yield fsExists && dbExists

  def isUploaded(namespace: Namespace, id: ObjectId): Future[Boolean] =
    for {
      dbUploaded <- objectRepository.isUploaded(namespace, id)
      fsExists <- blobStore.exists(namespace, id)
    } yield fsExists && dbUploaded

  def findBlob(namespace: Namespace, id: ObjectId): Future[(Long, HttpResponse)] = {
    for {
      _ <- ensureUploaded(namespace, id)
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

  private def ensureUploaded(namespace: Namespace, id: ObjectId): Future[ObjectId] = {
    isUploaded(namespace, id).flatMap {
      case true => Future.successful(id)
      case false => Future.failed(Errors.ObjectNotFound)
    }
  }

  def deleteByNamespace(namespace: Namespace): Future[Unit] = {
    for {
      _ <- blobStore.deleteByNamespace(namespace)
      _ <- objectRepository.deleteByNamespace(namespace)
    } yield ()
  }
}
