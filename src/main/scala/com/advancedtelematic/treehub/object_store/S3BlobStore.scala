package com.advancedtelematic.treehub.object_store

import java.io.File
import java.nio.file.Paths
import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant}
import java.util.Date
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{HttpResponse, StatusCodes, Uri}
import akka.stream.Materializer
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import com.advancedtelematic.common.DigestCalculator
import com.advancedtelematic.data.DataType.ObjectId
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.treehub.object_store.BlobStore.UploadAt
import com.amazonaws.HttpMethod
import com.amazonaws.auth.{AWSCredentials, AWSCredentialsProvider}
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.services.s3.model._
import org.slf4j.LoggerFactory
import cats.syntax.traverse._
import cats.instances.list._
import cats.instances.future._

import scala.async.Async._
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.{Random, Try}

object S3BlobStore {
  def apply(s3Credentials: S3Credentials, allowRedirects: Boolean)
           (implicit ec: ExecutionContext, mat: Materializer): S3BlobStore =
    new S3BlobStore(s3Credentials, S3Client(s3Credentials), allowRedirects)
}

class S3BlobStore(s3Credentials: S3Credentials, s3client: AmazonS3, allowRedirects: Boolean)
                 (implicit ec: ExecutionContext, mat: Materializer) extends BlobStore {

  private val DeleteObjectsBatchSize = 1000

  private val log = LoggerFactory.getLogger(this.getClass)

  private val bucketId = s3Credentials.blobBucketId

  private val maxAllowedReadLimit = 10 * 1024 * 1024 //10Mb

  override def storeStream(namespace: Namespace, id: ObjectId, size: Long, blob: Source[ByteString, _]): Future[Long] = {
    val filename = objectFilename(namespace, id)

    val sink =  StreamConverters.asInputStream().mapMaterializedValue { is =>
      val meta = new ObjectMetadata()
      meta.setContentLength(size)
      val request = new PutObjectRequest(s3Credentials.blobBucketId, filename, is, meta).withCannedAcl(CannedAccessControlList.AuthenticatedRead)

      // https://github.com/awsdocs/aws-java-developer-guide/blob/f5895eca4eb54302da434848ba7f7e6b25ed0dae/doc_source/best-practices.md
      request.getRequestClientOptions.setReadLimit(Try(size.toInt).fold(_ => maxAllowedReadLimit, size => maxAllowedReadLimit.min(size + 1)))

      log.info(s"Uploading $filename to amazon s3")

      async {
        await(Future { blocking { s3client.putObject(request) } })
        log.info(s"$filename with size $size uploaded to s3")
        size
      }
    }

    blob.runWith(sink)
  }

  override def storeOutOfBand(namespace: Namespace, id: ObjectId): Future[BlobStore.OutOfBandStoreResult] = {
    val filename = objectFilename(namespace, id)
    val expiresAt = Date.from(Instant.now().plus(1, ChronoUnit.HOURS))

    log.info(s"Requesting s3 pre signed url $filename")

    val f = Future {
      blocking {
        val url = s3client.generatePresignedUrl(s3Credentials.blobBucketId, filename, expiresAt, HttpMethod.PUT)
        log.debug(s"Signed s3 url for $filename")
        url
      }
    }

    f.map(url => UploadAt(url.toString))
  }

  protected def upload(file: File, filename: String): Future[Long] = {
    val request = new PutObjectRequest(s3Credentials.blobBucketId, filename, file)
      .withCannedAcl(CannedAccessControlList.AuthenticatedRead)

    log.info(s"Uploading $filename to amazon s3")

    async {
      await(Future { blocking { s3client.putObject(request) } })
      val metadata = await(Future { blocking { s3client.getObjectMetadata(bucketId, filename) } })

      val size = metadata.getContentLength

      log.info(s"$filename with size $size uploaded to s3")

      size
    }
  }

  private def streamS3Bytes(namespace: Namespace, id: ObjectId): Future[Source[ByteString, _]] = {
    val filename = objectFilename(namespace, id)
    Future {
      blocking {
        val s3ObjectInputStream = s3client.getObject(bucketId, filename).getObjectContent
        StreamConverters.fromInputStream(() ⇒ s3ObjectInputStream)
      }
    }
  }

  private def fetchPresignedUri(namespace: Namespace, id: ObjectId): Future[Uri] = {
    val publicExpireTime = Duration.ofDays(1)
    val filename = objectFilename(namespace, id)
    val expire = java.util.Date.from(Instant.now.plus(publicExpireTime))
    Future {
      val signedUri = blocking {
        s3client.generatePresignedUrl(bucketId, filename, expire)
      }
      Uri(signedUri.toURI.toString)
    }
  }

  override def buildResponse(namespace: Namespace, id: ObjectId): Future[HttpResponse] = {
    if(allowRedirects) {
      fetchPresignedUri(namespace, id).map { uri ⇒
        HttpResponse(StatusCodes.Found, headers = List(Location(uri)))
      }
    } else
      streamS3Bytes(namespace, id).map(buildResponseFromBytes)
  }

  override def readFull(namespace: Namespace, id: ObjectId): Future[ByteString] = {
    val filename = objectFilename(namespace, id)
    val is = s3client.getObject(bucketId, filename).getObjectContent
    val source = StreamConverters.fromInputStream(() => is)
    source.runFold(ByteString.empty)(_ ++ _)
  }

  override def exists(namespace: Namespace, id: ObjectId): Future[Boolean] = {
    val filename = objectFilename(namespace, id)
    Future { blocking { s3client.doesObjectExist(bucketId, filename) } }
  }

  private def namespaceDir(namespace: Namespace): String =
    DigestCalculator.digest()(namespace.get)

  private def objectFilename(namespace: Namespace, objectId: ObjectId): String =
    objectId.path(Paths.get(namespaceDir(namespace))).toString

  override val supportsOutOfBandStorage: Boolean = true

  override def deleteByNamespace(namespace: Namespace): Future[Unit] = {
    retrieveNamespaceObjects(namespace)
      .flatMap(deleteObjects)
      .map(deletedCount => log.info(s"Finished cleanup OSTree storage for namespace: $namespace. Deleted $deletedCount objects"))
  }

  private def retrieveNamespaceObjects(namespace: Namespace): Future[Seq[String]] = {
    def retrieveObjects(objectListing: Option[ObjectListing] = None, acc: Seq[String] = Seq.empty): Future[Seq[String]] =
      objectListing match {
        case None =>
          Future(blocking(s3client.listObjects(bucketId, Paths.get(namespaceDir(namespace)).toString)))
            .flatMap(listing => retrieveObjects(Some(listing), acc ++ listing.getObjectSummaries.asScala.map(_.getKey)))

        case Some(listing) if listing.isTruncated =>
          Future(blocking(s3client.listNextBatchOfObjects(listing)))
            .flatMap(nextListing => retrieveObjects(Some(nextListing), acc ++ nextListing.getObjectSummaries.asScala.map(_.getKey)))

        case Some(_) =>
          Future.successful(acc)
      }

    retrieveObjects()
  }


  private def deleteObjects(keys: Seq[String]): Future[Int] = {
    def delete(batches: List[Seq[String]], deletedAcc: Int = 0): Future[Int] = batches match {
      case head :: tail =>
        Future(blocking(s3client.deleteObjects(new DeleteObjectsRequest(bucketId).withKeys(head: _*)).getDeletedObjects.size()))
          .flatMap(deleted => delete(tail, deletedAcc + deleted))

      case Nil =>
        Future.successful(deletedAcc)
    }

    delete(keys.grouped(DeleteObjectsBatchSize).toList)
  }

}

object S3Client {
  private val _log = LoggerFactory.getLogger(this.getClass)

  def apply(s3Credentials: S3Credentials): AmazonS3 = {
    val defaultClientBuilder = AmazonS3ClientBuilder.standard().withCredentials(s3Credentials)

    if (s3Credentials.endpointUrl.length() > 0) {
      _log.info(s"Using custom S3 url: ${s3Credentials.endpointUrl}")
      defaultClientBuilder
        .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(s3Credentials.endpointUrl, s3Credentials.region.getName))
        .build()
    } else {
      defaultClientBuilder
        .withRegion(s3Credentials.region)
        .build()
    }
  }
}

class S3Credentials(accessKey: String, secretKey: String,
                    val blobBucketId: String,
                    val deltasBucketId: String,
                    val region: Regions,
                    val endpointUrl: String) extends AWSCredentials with AWSCredentialsProvider {
  override def getAWSAccessKeyId: String = accessKey

  override def getAWSSecretKey: String = secretKey

  override def refresh(): Unit = ()

  override def getCredentials: AWSCredentials = this
}
