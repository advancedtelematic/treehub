package com.advancedtelematic.treehub.delta_store

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.advancedtelematic.data.DataType.ValidDeltaId
import com.advancedtelematic.libats.data.Namespace
import com.advancedtelematic.treehub.delta_store.StaticDeltaStorage.StaticDeltaRedirectResponse
import com.advancedtelematic.treehub.http.Errors
import com.advancedtelematic.util.TreeHubSpec
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.scalatest.BeforeAndAfterAll
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.ExecutionContext

class S3DeltaStorageIntegrationSpec extends TreeHubSpec with BeforeAndAfterAll {
  implicit val ec = ExecutionContext.global

  implicit lazy val system = ActorSystem("S3BlobStoreSpec")

  implicit lazy val mat = ActorMaterializer()

  val s3DeltaStore = new S3DeltaStorage(s3Credentials)

  val s3Client = AmazonS3ClientBuilder.standard()
    .withCredentials(s3Credentials)
    .withRegion(s3Credentials.region)
    .build()

  override implicit def patienceConfig = PatienceConfig().copy(timeout = Span(5, Seconds))

  import com.advancedtelematic.libats.data.RefinedUtils.RefineTry

  val deltaId = "18/181901808-19279127".refineTry[ValidDeltaId].get

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    val summaryPath = s3DeltaStore.summaryPath(defaultNs)
    val superBlockPath = s3DeltaStore.deltaDir(defaultNs, deltaId).resolve("superblock")

    s3Client.putObject(s3Credentials.deltasBucketId, summaryPath.toString, "some summary")

    s3Client.putObject(s3Credentials.deltasBucketId, superBlockPath.toString, "superblockstuff")
  }

  test("can retrieve delta superblock") {
    val result = s3DeltaStore.retrieve(defaultNs, deltaId, "superblock").futureValue
    result shouldBe a[StaticDeltaRedirectResponse]
    result.asInstanceOf[StaticDeltaRedirectResponse].length shouldBe "superblockstuff".getBytes.length
  }

  test("can retrieve delta summary") {
    val result = s3DeltaStore.summary(defaultNs).futureValue
    val summary = result.runReduce(_ ++ _).futureValue.utf8String
    summary shouldBe "some summary"
  }

  test("returns proper error if summary does not exist") {
    val result = s3DeltaStore.summary(Namespace("doesnotexist")).failed.futureValue
    result shouldBe Errors.SummaryDoesNotExist
  }

  test("returns proper error if delta superblock does not exist") {
    val result = s3DeltaStore.retrieve(Namespace("doesnotexist"), deltaId, "superblock").failed.futureValue
    result shouldBe Errors.StaticDeltaDoesNotExist
  }
}
