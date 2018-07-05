package com.advancedtelematic.treehub

import java.nio.file.Paths

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.{Directives, Route}
import com.advancedtelematic.libats.http.BootApp
import com.advancedtelematic.libats.messaging.MessageBus
import com.advancedtelematic.libats.slick.db.{BootMigrations, DatabaseConfig}
import com.advancedtelematic.libats.slick.monitoring.DatabaseMetrics
import com.advancedtelematic.treehub.client._
import com.advancedtelematic.treehub.http.{TreeHubRoutes, Http => TreeHubHttp}
import com.advancedtelematic.treehub.object_store.{LocalFsBlobStore, ObjectStore, S3BlobStore, S3Credentials}
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter
import com.amazonaws.regions.Regions
import com.typesafe.config.ConfigFactory
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.http.VersionDirectives._
import com.advancedtelematic.libats.http.LogDirectives._
import com.advancedtelematic.libats.http.monitoring.MetricsSupport
import com.advancedtelematic.metrics.{AkkaHttpRequestMetrics, InfluxdbMetricsReporterSupport}
import com.advancedtelematic.metrics.prometheus.PrometheusMetricsSupport
import com.advancedtelematic.treehub.delta_store.{LocalDeltaStorage, S3DeltaStorage}


trait Settings {
  private lazy val _config = ConfigFactory.load()

  val host = _config.getString("server.host")
  val port = _config.getInt("server.port")
  val coreUri = _config.getString("core.baseUri")
  val packagesApi = _config.getString("core.packagesApi")

  val treeHubUri = {
    val uri = Uri(_config.getString("server.treehubUri"))
    if(!uri.isAbsolute) throw new IllegalArgumentException("Treehub host is not an absolute uri")
    uri
  }

  val localStorePath = Paths.get(_config.getString("treehub.localStorePath"))

  val deviceRegistryUri = Uri(_config.getString("device_registry.baseUri"))
  val deviceRegistryMyApi = Uri(_config.getString("device_registry.mydeviceUri"))

  lazy val s3Credentials = {
    val accessKey = _config.getString("treehub.s3.accessKey")
    val secretKey = _config.getString("treehub.s3.secretKey")
    val objectBucketId = _config.getString("treehub.s3.bucketId")
    val deltasBucketId = _config.getString("treehub.s3.deltasBucketId")
    val region = Regions.fromName(_config.getString("treehub.s3.region"))

    new S3Credentials(accessKey, secretKey, objectBucketId, deltasBucketId, region)
  }

  lazy val useS3 = _config.getString("treehub.storage").equals("s3")

  lazy val allowRedirectsToS3 = _config.getBoolean("treehub.s3.allowRedirects")
}

object Boot extends BootApp with Directives with Settings with VersionInfo
  with BootMigrations
  with DatabaseConfig
  with MetricsSupport
  with DatabaseMetrics
  with InfluxdbMetricsReporterSupport
  with AkkaHttpRequestMetrics
  with PrometheusMetricsSupport {

  implicit val _db = db

  log.info(s"Starting $version on http://$host:$port")

  val deviceRegistry = new DeviceRegistryHttpClient(deviceRegistryUri, deviceRegistryMyApi)

  val tokenValidator = TreeHubHttp.tokenValidator
  val namespaceExtractor = TreeHubHttp.extractNamespace.map(_.namespace.get).map(Namespace.apply)
  val deviceNamespace = TreeHubHttp.deviceNamespace(deviceRegistry)

  lazy val objectStorage = {
    if(useS3) {
      log.info("Using s3 storage for object blobs")
      new S3BlobStore(s3Credentials, allowRedirectsToS3)
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
  val msgPublisher = MessageBus.publisher(system, config)
  val coreHttpClient = new CoreHttpClient(coreUri, packagesApi, treeHubUri)
  val coreBusClient = new CoreBusClient(msgPublisher, treeHubUri)

  val usageHandler = system.actorOf(UsageMetricsRouter(msgPublisher, objectStore), "usage-router")

  val routes: Route =
    (versionHeaders(version) & requestMetrics(metricRegistry) & logResponseMetrics(projectName)) {
      prometheusMetricsRoutes ~
      new TreeHubRoutes(tokenValidator, namespaceExtractor, coreHttpClient, coreBusClient,
                        deviceNamespace, objectStore, deltaStorage, usageHandler).routes
    }

  Http().bindAndHandle(routes, host, port)
}
