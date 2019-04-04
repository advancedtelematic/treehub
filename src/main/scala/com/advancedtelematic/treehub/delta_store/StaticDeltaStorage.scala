package com.advancedtelematic.treehub.delta_store

import java.io.IOException
import java.nio.file.{Files, Path, Paths}
import java.time.{Duration, Instant}

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import com.advancedtelematic.data.DataType.DeltaId
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.treehub.delta_store.StaticDeltaStorage.{StaticDeltaContent, StaticDeltaRedirectResponse, StaticDeltaResponse}
import com.advancedtelematic.treehub.http.Errors
import com.advancedtelematic.treehub.object_store.S3Credentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.concurrent.blocking

trait StaticDeltaStorage {
  protected[delta_store] def namespaceDir(namespace: Namespace): Path = {
    val digest = DigestUtils.sha256(namespace.get)
    Paths.get(Hex.encodeHexString(digest))
  }

  protected[delta_store] def deltaDir(namespace: Namespace, deltaId: DeltaId): Path  =
    namespaceDir(namespace).resolve(deltaId.urlSafe)

  protected[delta_store] def summaryPath(namespace: Namespace): Path =
    namespaceDir(namespace).resolve("summary")

  def summary(namespace: Namespace): Future[Source[ByteString, _]]

  def retrieve(namespace: Namespace, deltaId: DeltaId, path: String): Future[StaticDeltaResponse]
}

object StaticDeltaStorage {
  sealed trait StaticDeltaResponse {
    val length: Long
  }
  case class StaticDeltaRedirectResponse(location: Uri, length: Long) extends StaticDeltaResponse
  case class StaticDeltaContent(data: Source[ByteString, _], length: Long) extends StaticDeltaResponse
}


class S3DeltaStorage(s3Credentials: S3Credentials)(implicit ec: ExecutionContext) extends StaticDeltaStorage {
  private val _log = LoggerFactory.getLogger(this.getClass)

  protected lazy val s3client = {
    if(s3Credentials.endpointUrl.length() > 0) {
      _log.info(s"Using custom S3 url: ${s3Credentials.endpointUrl}")
      AmazonS3ClientBuilder.standard()
        .withCredentials(s3Credentials)
        .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(s3Credentials.endpointUrl, s3Credentials.region.getName()))
    } else {
      AmazonS3ClientBuilder.standard()
        .withCredentials(s3Credentials)
        .withRegion(s3Credentials.region)
    }
  }.build()

  override def summary(namespace: Namespace): Future[Source[ByteString, _]] = {
    val path = summaryPath(namespace)

    objectExists(path).flatMap {
      case true => downloadSummary(namespace)
      case false => FastFuture.failed(Errors.SummaryDoesNotExist)
    }
  }

  private def objectExists(path: Path): Future[Boolean] = {
    Future {
      blocking {
        _log.info(s"getting summary at ${s3Credentials.deltasBucketId}/${path.toString}")
        s3client.doesObjectExist(s3Credentials.deltasBucketId, path.toString)
      }
    }
  }

  private def downloadSummary(namespace: Namespace): Future[Source[ByteString, _]] = {
    Future {
      blocking {
        val obj = s3client.getObject(s3Credentials.deltasBucketId, summaryPath(namespace).toString)
        val s3ObjectInputStream = obj.getObjectContent
        StreamConverters.fromInputStream(() => s3ObjectInputStream)
      }
    }
  }

  private def retrieveExistingDelta(namespace: Namespace, deltaId: DeltaId, path: String): Future[StaticDeltaResponse] = {
    val publicExpireTime = Duration.ofDays(1)
    val expire = java.util.Date.from(Instant.now.plus(publicExpireTime))
    Future {
      val filePath = deltaDir(namespace, deltaId).resolve(path)

      val signedUri = blocking {
        s3client.generatePresignedUrl(s3Credentials.deltasBucketId, filePath.toString, expire)
      }
      val size = blocking {
        s3client.getObjectMetadata(s3Credentials.deltasBucketId, filePath.toString).getContentLength
      }

      StaticDeltaRedirectResponse(Uri(signedUri.toURI.toString), size)
    }
  }


  override def retrieve(namespace: Namespace, deltaId: DeltaId, path: String): Future[StaticDeltaResponse] = {
    val filePath = deltaDir(namespace, deltaId).resolve(path)
    objectExists(filePath).flatMap {
      case true => retrieveExistingDelta(namespace, deltaId, path)
      case false => FastFuture.failed(Errors.StaticDeltaDoesNotExist)
    }
  }
}

class LocalDeltaStorage(root: Path) extends StaticDeltaStorage {
  private val _log = LoggerFactory.getLogger(this.getClass)

  override def summary(namespace: Namespace): Future[Source[ByteString, _]] = {
    val path = root.resolve(namespaceDir(namespace).resolve("summary"))
    if(Files.exists(path)) {
      val is = Files.newInputStream(path)
      Future.successful(StreamConverters.fromInputStream(() => is))
    } else {
      FastFuture.failed(Errors.SummaryDoesNotExist)
    }
  }

  override def retrieve(namespace: Namespace, deltaId: DeltaId, path: String): Future[StaticDeltaResponse] = {
    val file = root.resolve(deltaDir(namespace, deltaId)).resolve(path)
    val is = Files.newInputStream(file)
    Future.successful(StaticDeltaContent(StreamConverters.fromInputStream(() => is), file.toFile.length()))
  }

  def storeSummary(namespace: Namespace, data: Array[Byte]): Future[Unit] = {
    val path = root.resolve(namespaceDir(namespace).resolve("summary"))
    ensureDirExists(path)
    Files.write(path, data)
    Future.successful(())
  }

  def storeBytes(namespace: Namespace, deltaId: DeltaId, objPath: String, bytes: Array[Byte]): Future[Unit] = {
    val path = root.resolve(deltaDir(namespace, deltaId).resolve(objPath))
    ensureDirExists(path)
    Files.write(path, bytes)
    Future.successful(())
  }

  private def ensureDirExists(path: Path) = {
    Try(Files.createDirectories(path.getParent)).failed.foreach { ex =>
      _log.warn("Could not create directories", ex)
    }
  }
}
