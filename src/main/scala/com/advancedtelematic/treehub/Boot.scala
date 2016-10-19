package com.advancedtelematic.treehub

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.{ActorMaterializer, Materializer}
import com.advancedtelematic.treehub.http.{ConfResource, ObjectResource, RefResource}
import com.typesafe.config.ConfigFactory
import org.genivi.sota.db.BootMigrations
import org.genivi.sota.http.{ErrorHandler, HealthResource}
import org.slf4j.LoggerFactory
import slick.driver.MySQLDriver.api._
import org.genivi.sota.rest.SotaRejectionHandler.rejectionHandler
import org.genivi.sota.http.VersionDirectives.versionHeaders
import org.genivi.sota.http.LogDirectives.logResponseMetrics

import scala.concurrent.ExecutionContext

class TreeHubRoutes()
                   (implicit val db: Database, ec: ExecutionContext, mat: Materializer) extends VersionInfo {

  import Directives._

  val routes: Route =
    handleRejections(rejectionHandler) {
      ErrorHandler.handleErrors {
        pathPrefix("api" / "v1") {
          new ConfResource().route ~
          new ObjectResource(http.Http.extractNamespace).route ~
            new RefResource(http.Http.extractNamespace).route
        } ~ new HealthResource(db, versionMap).route
      }
    }
}

trait Settings {
  lazy val config = ConfigFactory.load()

  val host = config.getString("server.host")
  val port = config.getInt("server.port")
}

object Boot extends App with Directives with Settings with VersionInfo with BootMigrations {

  val _log = LoggerFactory.getLogger(this.getClass)

  implicit val _db = Database.forConfig("database")

  implicit val _system = ActorSystem()
  implicit val _mat = ActorMaterializer()

  // TODO: Use different dispatcher
  import _system.dispatcher

  _log.info(s"Starting $version on http://$host:$port")

  val routes: Route =
    (versionHeaders(version) & logResponseMetrics(projectName)) {
      new TreeHubRoutes().routes
    }

  Http().bindAndHandle(routes, host, port)
}
