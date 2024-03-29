package com.advancedtelematic.treehub.object_store

import java.nio.file.StandardOpenOption.{CREATE, READ, WRITE}
import java.nio.file.{Files, Path}
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import com.advancedtelematic.data.DataType.ObjectId
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.treehub.http.Errors
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.io.Directory
import scala.util.Try


object LocalFsBlobStore {
  private val _log = LoggerFactory.getLogger(this.getClass)

  def apply(root: Path)(implicit mat: Materializer, ec: ExecutionContext): LocalFsBlobStore = {
    if(!root.toFile.exists() && !root.getParent.toFile.canWrite) {
      throw new IllegalArgumentException(s"Could not open $root as local blob store")
    } else if (!root.toFile.exists()) {
      Files.createDirectories(root)
      _log.info(s"Created local fs blob store directory: $root")
    }

    _log.info(s"local fs blob store set to $root")
    new LocalFsBlobStore(root)
  }
}

class LocalFsBlobStore(root: Path)(implicit ec: ExecutionContext, mat: Materializer) extends BlobStore {
  def store(ns: Namespace, id: ObjectId, blob: Source[ByteString, _]): Future[(Path, Long)] = {
    for {
      path <- Future.fromTry(objectPath(ns, id))
      ioResult <- blob.runWith(FileIO.toPath(path, options = Set(READ, WRITE, CREATE)))
      res <- {
        if (ioResult.wasSuccessful) {
          Future.successful(ioResult.count)
        } else {
          Future.failed(BlobStoreError(s"Error storing local blob ${ioResult.getError.getLocalizedMessage}", ioResult.getError))
        }
      }
    } yield path -> res
  }

  override def storeStream(namespace: Namespace, id: ObjectId, size: Long, blob: Source[ByteString, _]): Future[Long] =
    store(namespace, id, blob).map(_._2)

  override def storeOutOfBand(namespace: Namespace, id: ObjectId): Future[BlobStore.OutOfBandStoreResult] =
    FastFuture.failed(Errors.OutOfBandStorageNotSupported)

  override def buildResponse(ns: Namespace, id: ObjectId): Future[HttpResponse] = {
    exists(ns, id).flatMap {
      case true =>
        val triedResponse =
          objectPath(ns, id)
            .map(FileIO.fromPath(_))
            .map(buildResponseFromBytes)
        Future.fromTry(triedResponse)
      case false => Future.failed(Errors.BlobNotFound)
    }
  }

  override def readFull(ns: Namespace, id: ObjectId): Future[ByteString] = {
    buildResponse(ns, id).flatMap { response =>
      val dataBytes = response.entity.dataBytes
      dataBytes.runFold(ByteString.empty)(_ ++ _)
    }
  }

  override def exists(ns: Namespace, id: ObjectId): Future[Boolean] = {
    val path = objectPath(ns, id).flatMap(p => Try(Files.exists(p)))
    Future.fromTry(path)
  }

  private def namespacePath(ns: Namespace): Path =
    root.toAbsolutePath.resolve(ns.get)

  private def objectPath(ns: Namespace, id: ObjectId): Try[Path] = {
    val path = id.path(namespacePath(ns))

    Try {
      if (Files.notExists(path.getParent))
        Files.createDirectories(path.getParent)
      path
    }
  }

  override val supportsOutOfBandStorage: Boolean = false

  override def deleteByNamespace(namespace: Namespace): Future[Unit] = Future.fromTry {
    Try {
      val directory = new Directory(namespacePath(namespace).toFile)

      directory.deleteRecursively()
    }
  }
}
