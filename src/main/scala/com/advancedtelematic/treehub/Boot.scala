package com.advancedtelematic.treehub

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import com.advancedtelematic.treehub.http.{Http => TreeHubHttp}
import com.advancedtelematic.treehub.http.{CoreClient, TreeHubRoutes}
import com.typesafe.config.ConfigFactory
import org.genivi.sota.db.BootMigrations
import org.genivi.sota.client.DeviceRegistryClient
import org.genivi.sota.http.LogDirectives.logResponseMetrics
import org.genivi.sota.http.VersionDirectives.versionHeaders
import org.slf4j.LoggerFactory
import slick.driver.MySQLDriver.api._


trait Settings {
  lazy val config = ConfigFactory.load()

  val host = config.getString("server.host")
  val port = config.getInt("server.port")
  val coreUri = config.getString("core.baseUri")
  val packagesApi = config.getString("core.packagesApi")
  val treeHubUri = "https://" + config.getString("server.treeHubHost") + "/api/v1/mydevice"

  val deviceRegistryUri = Uri(config.getString("device_registry.baseUri"))
  val deviceRegistryApi = Uri(config.getString("device_registry.devicesUri"))
  val deviceRegistryGroupApi = Uri(config.getString("device_registry.deviceGroupsUri"))
  val deviceRegistryMyApi = Uri(config.getString("device_registry.mydeviceUri"))

}

object Boot extends App with Directives with Settings with VersionInfo with BootMigrations {

  val _log = LoggerFactory.getLogger(this.getClass)

  implicit val _db = Database.forConfig("database")

  implicit val _system = ActorSystem()
  implicit val _mat = ActorMaterializer()

  // TODO: Use different dispatcher
  import _system.dispatcher

  _log.info(s"Starting $version on http://$host:$port")

  val deviceRegistry = new DeviceRegistryClient(
    deviceRegistryUri, deviceRegistryApi, deviceRegistryGroupApi, deviceRegistryMyApi
  )

  val tokenValidator = TreeHubHttp.tokenValidator
  val namespaceExtractor = (api: String) => TreeHubHttp.extractNamespace(api, allowEmpty = false)
  val deviceNamespace = TreeHubHttp.deviceNamespace(deviceRegistry)

  val coreClient = new CoreClient(coreUri, packagesApi, treeHubUri)

  val routes: Route =
    (versionHeaders(version) & logResponseMetrics(projectName)) {
      new TreeHubRoutes(tokenValidator, namespaceExtractor, coreClient, deviceNamespace).routes
    }

  Http().bindAndHandle(routes, host, port)
}
