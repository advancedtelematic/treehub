package com.advancedtelematic.ota_treehub

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.{ActorMaterializer, Materializer}
import com.advancedtelematic.ota_treehub.http.{ConfResource, ObjectResource, RefResource}
import com.typesafe.config.ConfigFactory
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
          new ObjectResource().route ~
            new RefResource().route
        } ~ new HealthResource(db, versionMap).route
      }
    }
}

trait Settings {
  lazy val config = ConfigFactory.load()

  val host = config.getString("server.host")
  val port = config.getInt("server.port")
}

object Boot extends App with Directives with Settings with VersionInfo {

  val _log = LoggerFactory.getLogger(this.getClass)

  implicit val _db = Database.forConfig("database")

  implicit val _system = ActorSystem()
  implicit val _mat = ActorMaterializer()

  // TODO: Use different dispatcher
  import _system.dispatcher

  _log.info(s"Starting $version on http://$host:$port")

  val routes: Route =
    versionHeaders(version) {
      Route.seal {
        logResponseMetrics(projectName) {
          logRequestResult((projectName, Logging.InfoLevel)) {
            new TreeHubRoutes().routes
          }
        }
      }
  }

  Http().bindAndHandle(routes, host, port)
}
