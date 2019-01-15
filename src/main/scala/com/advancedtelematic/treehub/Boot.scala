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
import com.advancedtelematic.treehub.daemon.StaleObjectArchiveActor
import com.advancedtelematic.treehub.delta_store.{LocalDeltaStorage, S3DeltaStorage}


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

  if(objectStorage.supportsOutOfBandStorage) {
    system.actorOf(StaleObjectArchiveActor.withBackOff(objectStorage, staleObjectExpireAfter, autoStart = true), "stale-objects-archiver")
  }

  val routes: Route =
    (versionHeaders(version) & requestMetrics(metricRegistry) & logResponseMetrics(projectName)) {
      prometheusMetricsRoutes ~
      new TreeHubRoutes(tokenValidator, namespaceExtractor, coreHttpClient, coreBusClient,
                        deviceNamespace, objectStore, deltaStorage, usageHandler).routes
    }

  Http().bindAndHandle(routes, host, port)
}
