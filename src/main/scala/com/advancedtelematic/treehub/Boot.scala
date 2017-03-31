package com.advancedtelematic.treehub

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import cats.data.Xor
import com.advancedtelematic.treehub.client._
import com.advancedtelematic.treehub.http.{TreeHubRoutes, Http => TreeHubHttp}
import com.advancedtelematic.treehub.object_store.{LocalFsBlobStore, ObjectStore, S3BlobStore, S3Credentials}
import com.advancedtelematic.treehub.repo_metrics.{StorageUpdate, UsageMetricsRouter}
import com.amazonaws.regions.Regions
import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.{ConfigException, ConfigFactory}
import org.genivi.sota.db.{BootMigrations, DatabaseConfig}
import org.genivi.sota.client.DeviceRegistryClient
import org.genivi.sota.http.BootApp
import org.genivi.sota.http.LogDirectives.logResponseMetrics
import org.genivi.sota.http.VersionDirectives.versionHeaders
import org.genivi.sota.messaging.MessageBus
import org.genivi.sota.monitoring.{DatabaseMetrics, MetricsSupport}
import org.slf4j.LoggerFactory

import scala.util.Try

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

  val localStorePath = config.getString("treehub.localStorePath")

  val deviceRegistryUri = Uri(config.getString("device_registry.baseUri"))
  val deviceRegistryApi = Uri(config.getString("device_registry.devicesUri"))
  val deviceRegistryGroupApi = Uri(config.getString("device_registry.deviceGroupsUri"))
  val deviceRegistryMyApi = Uri(config.getString("device_registry.mydeviceUri"))
  val deviceRegistryPackagesUri = Uri(config.getString("device_registry.packagesUri"))

  lazy val s3Credentials = {
    val accessKey = config.getString("treehub.s3.accessKey")
    val secretKey = config.getString("treehub.s3.secretKey")
    val bucketId = config.getString("treehub.s3.bucketId")
    val region = Regions.fromName(config.getString("treehub.s3.region"))
    new S3Credentials(accessKey, secretKey, bucketId, region)
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

  val deviceRegistry = new DeviceRegistryClient(
    deviceRegistryUri, deviceRegistryApi, deviceRegistryGroupApi, deviceRegistryMyApi, deviceRegistryPackagesUri
  )

  val tokenValidator = TreeHubHttp.tokenValidator
  val namespaceExtractor = TreeHubHttp.extractNamespace.map(_.namespace)
  val deviceNamespace = TreeHubHttp.deviceNamespace(deviceRegistry)

  lazy val storage = {
    if(useS3) {
      log.info("Using s3 storage for object blobs")
      new S3BlobStore(s3Credentials)
    } else {
      log.info(s"Using local storage a t$localStorePath for object blobs")
      LocalFsBlobStore(localStorePath)
    }
  }

  val objectStore = new ObjectStore(storage)
  val msgPublisher = MessageBus.publisher(system, config) match {
    case Xor.Right(mbp) => mbp
    case Xor.Left(err) => throw err
  }
  val coreHttpClient = new CoreHttpClient(coreUri, packagesApi, treeHubUri)
  val coreBusClient = new CoreBusClient(msgPublisher, treeHubUri)

  val usageHandler = system.actorOf(UsageMetricsRouter(msgPublisher, objectStore), "usage-router")

  val routes: Route =
    (versionHeaders(version) & logResponseMetrics(projectName) & TreeHubHttp.transformAtsAuthHeader) {
      new TreeHubRoutes(tokenValidator, namespaceExtractor, coreHttpClient, coreBusClient,
                        deviceNamespace, objectStore, usageHandler).routes
    }

  Http().bindAndHandle(routes, host, port)
}
