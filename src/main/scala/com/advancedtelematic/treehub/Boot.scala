package com.advancedtelematic.treehub

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import com.advancedtelematic.treehub.client.CoreClient
import com.advancedtelematic.treehub.http.{TreeHubRoutes, Http => TreeHubHttp}
import com.advancedtelematic.treehub.object_store.{LocalFsBlobStore, ObjectStore}
import com.typesafe.config.ConfigFactory
import org.genivi.sota.db.{BootMigrations, DatabaseConfig}
import org.genivi.sota.client.DeviceRegistryClient
import org.genivi.sota.http.LogDirectives.logResponseMetrics
import org.genivi.sota.http.VersionDirectives.versionHeaders
import org.genivi.sota.monitoring.{DatabaseMetrics, MetricsSupport}
import org.slf4j.LoggerFactory

trait Settings {
  lazy val config = ConfigFactory.load()

  val host = config.getString("server.host")
  val port = config.getInt("server.port")
  val coreUri = config.getString("core.baseUri")
  val packagesApi = config.getString("core.packagesApi")

  val treeHubUri = {
    val uri = Uri(config.getString("server.treeHubHost"))
    if(!uri.isAbsolute) throw new IllegalArgumentException("Treehub host is not an absolute uri")
    uri
  }

  val localStorePath = config.getString("treehub.localStorePath")

  val deviceRegistryUri = Uri(config.getString("device_registry.baseUri"))
  val deviceRegistryApi = Uri(config.getString("device_registry.devicesUri"))
  val deviceRegistryGroupApi = Uri(config.getString("device_registry.deviceGroupsUri"))
  val deviceRegistryMyApi = Uri(config.getString("device_registry.mydeviceUri"))

}

object Boot extends App with Directives with Settings with VersionInfo
  with BootMigrations
  with DatabaseConfig
  with MetricsSupport
  with DatabaseMetrics {

  val _log = LoggerFactory.getLogger(this.getClass)

  implicit val _db = db

  implicit val _system = ActorSystem()
  implicit val _mat = ActorMaterializer()

  // TODO: Use different dispatcher
  import _system.dispatcher

  _log.info(s"Starting $version on http://$host:$port")

  val deviceRegistry = new DeviceRegistryClient(
    deviceRegistryUri, deviceRegistryApi, deviceRegistryGroupApi, deviceRegistryMyApi
  )

  val tokenValidator = TreeHubHttp.tokenValidator
  val namespaceExtractor = TreeHubHttp.extractNamespace.map(_.namespace)
  val deviceNamespace = TreeHubHttp.deviceNamespace(deviceRegistry)
  val objectStore = new ObjectStore(LocalFsBlobStore(localStorePath))
  val coreClient = new CoreClient(coreUri, packagesApi, treeHubUri.toString())

  val routes: Route =
    (versionHeaders(version) & logResponseMetrics(projectName)) {
      new TreeHubRoutes(tokenValidator, namespaceExtractor, coreClient, deviceNamespace, objectStore).routes
    }

  Http().bindAndHandle(routes, host, port)
}
