package com.advancedtelematic.treehub

import java.nio.file.Paths

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.{Directives, Route}
import com.advancedtelematic.libats.http.BootApp
import com.advancedtelematic.libats.messaging.MessageBus
import com.advancedtelematic.libats.monitoring.MetricsSupport
import com.advancedtelematic.libats.slick.db.{BootMigrations, DatabaseConfig}
import com.advancedtelematic.libats.slick.monitoring.DatabaseMetrics
import com.advancedtelematic.treehub.client._
import com.advancedtelematic.treehub.http.{TreeHubRoutes, Http => TreeHubHttp}
import com.advancedtelematic.treehub.object_store.{LocalFsBlobStore, ObjectStore, S3BlobStore, S3Credentials}
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter
import com.amazonaws.regions.Regions
import com.typesafe.config.ConfigFactory
import cats.syntax.either._
import com.advancedtelematic.libats.data.Namespace
import com.advancedtelematic.libats.http.VersionDirectives._
import com.advancedtelematic.libats.http.LogDirectives._
import com.advancedtelematic.treehub.delta_store.{LocalDeltaStorage, S3DeltaStorage}


trait Settings {
  lazy val config = ConfigFactory.load()

  val host = config.getString("server.host")
  val port = config.getInt("server.port")
  val coreUri = config.getString("core.baseUri")
  val packagesApi = config.getString("core.packagesApi")

  val treeHubUri = {
    val uri = Uri(config.getString("server.treehubUri"))
    if(!uri.isAbsolute) throw new IllegalArgumentException("Treehub host is not an absolute uri")
    uri
  }

  val localStorePath = Paths.get(config.getString("treehub.localStorePath"))

  val deviceRegistryUri = Uri(config.getString("device_registry.baseUri"))
  val deviceRegistryMyApi = Uri(config.getString("device_registry.mydeviceUri"))

  lazy val s3Credentials = {
    val accessKey = config.getString("treehub.s3.accessKey")
    val secretKey = config.getString("treehub.s3.secretKey")
    val objectBucketId = config.getString("treehub.s3.bucketId")
    val deltasBucketId = config.getString("treehub.s3.deltasBucketId")
    val region = Regions.fromName(config.getString("treehub.s3.region"))

    new S3Credentials(accessKey, secretKey, objectBucketId, deltasBucketId, region)
  }

  lazy val useS3 = config.getString("treehub.storage").equals("s3")
}

object Boot extends BootApp with Directives with Settings with VersionInfo
  with BootMigrations
  with DatabaseConfig
  with MetricsSupport
  with DatabaseMetrics {

  implicit val _db = db

  log.info(s"Starting $version on http://$host:$port")

  val deviceRegistry = new DeviceRegistryHttpClient(deviceRegistryUri, deviceRegistryMyApi)

  val tokenValidator = TreeHubHttp.tokenValidator
  val namespaceExtractor = TreeHubHttp.extractNamespace.map(_.namespace.get).map(Namespace.apply)
  val deviceNamespace = TreeHubHttp.deviceNamespace(deviceRegistry)

  lazy val objectStorage = {
    if(useS3) {
      log.info("Using s3 storage for object blobs")
      new S3BlobStore(s3Credentials)
    } else {
      log.info(s"Using local storage a t$localStorePath for object blobs")
      LocalFsBlobStore(localStorePath.resolve("object-storage"))
    }
  }

  lazy val deltaStorage = {
    if(useS3) {
      log.info("Using s3 storage for object blobs")
      new S3DeltaStorage(s3Credentials)
    } else {
      log.info(s"Using local storage at $localStorePath for object blobs")
      new LocalDeltaStorage(localStorePath.resolve("delta-storage"))
    }
  }

  val objectStore = new ObjectStore(objectStorage)
  val msgPublisher = MessageBus.publisher(system, config).valueOr(throw _)
  val coreHttpClient = new CoreHttpClient(coreUri, packagesApi, treeHubUri)
  val coreBusClient = new CoreBusClient(msgPublisher, treeHubUri)

  val usageHandler = system.actorOf(UsageMetricsRouter(msgPublisher, objectStore), "usage-router")

  val routes: Route =
    (versionHeaders(version) & logResponseMetrics(projectName) & TreeHubHttp.transformAtsAuthHeader) {
      new TreeHubRoutes(tokenValidator, namespaceExtractor, coreHttpClient, coreBusClient,
                        deviceNamespace, objectStore, deltaStorage, usageHandler).routes
    }

  Http().bindAndHandle(routes, host, port)
}
