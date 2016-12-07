package com.advancedtelematic.treehub.object_store

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.advancedtelematic.data.DataType.{ObjectId, TObject}
import com.advancedtelematic.treehub.db.ObjectRepositorySupport
import org.genivi.sota.data.Namespace
import org.slf4j.LoggerFactory
import slick.driver.MySQLDriver.api._

import scala.concurrent.{ExecutionContext, Future}

class ObjectStore(blobStore: BlobStore)(implicit ec: ExecutionContext, db: Database) extends ObjectRepositorySupport {
  def store(namespace: Namespace, id: ObjectId, blob: Source[ByteString, _]): Future[TObject] = {
    val tobj = TObject(namespace, id)

    for {
      _ <- objectRepository.create(tobj)
      _ <- blobStore.store(namespace, id, blob)
    } yield tobj
  }

  def exists(namespace: Namespace, id: ObjectId): Future[Boolean] =
    for {
      dbExists <- objectRepository.exists(namespace, id)
      fsExists <- blobStore.exists(namespace, id)
    } yield fsExists && dbExists

  def findBlob(namespace: Namespace, id: ObjectId): Future[Source[ByteString, _]] = {
    blobStore.find(namespace, id)
  }

  def readFull(namespace: Namespace, id: ObjectId): Future[ByteString] = {
    blobStore.readFull(namespace, id)
  }
}
