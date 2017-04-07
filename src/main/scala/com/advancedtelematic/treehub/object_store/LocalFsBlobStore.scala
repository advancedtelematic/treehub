package com.advancedtelematic.treehub.object_store

import java.io.File
import java.nio.file.{Files, Path, Paths}

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import com.advancedtelematic.data.DataType.ObjectId
import com.advancedtelematic.treehub.http.Errors
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import java.nio.file.StandardOpenOption.{CREATE, READ, WRITE}

import akka.http.scaladsl.model.HttpResponse
import com.advancedtelematic.libats.data.Namespace


object LocalFsBlobStore {
  private val _log = LoggerFactory.getLogger(this.getClass)

  def apply(root: String)(implicit mat: Materializer, ec: ExecutionContext): LocalFsBlobStore = {
    val f = new File(root)
    if(!f.exists() && !f.getParentFile.canWrite) {
      throw new IllegalArgumentException(s"Could not open $root as local blob store")
    } else if (!f.exists()) {
      Files.createDirectory(f.toPath)
      _log.info(s"Created local fs blob store directory: $root")
    }

    _log.info(s"local fs blob store set to $root")
    new LocalFsBlobStore(f)
  }
}

class LocalFsBlobStore(root: File)(implicit ec: ExecutionContext, mat: Materializer) extends BlobStore {
  override def store(ns: Namespace, id: ObjectId, blob: Source[ByteString, _]): Future[Long] = {
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
    } yield res
  }

  override def buildResponse(ns: Namespace, id: ObjectId, clientAcceptsRedirects: Boolean): Future[HttpResponse] = {
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

  private def namespacePath(ns: Namespace): Path = {
    Paths.get(root.getAbsolutePath, ns.get)
  }

  private def objectPath(ns: Namespace, id: ObjectId): Try[Path] = {
    val path = id.path(namespacePath(ns))

    Try {
      if (Files.notExists(path.getParent))
        Files.createDirectories(path.getParent)
      path
    }
  }
}
