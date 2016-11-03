package com.advancedtelematic.treehub.http

import akka.http.scaladsl.server.{Directives, _}
import akka.stream.Materializer
import com.advancedtelematic.treehub.VersionInfo
import org.genivi.sota.http.{ErrorHandler, HealthResource}
import org.genivi.sota.rest.SotaRejectionHandler._

import scala.concurrent.ExecutionContext
import slick.driver.MySQLDriver.api._


class TreeHubRoutes()
                   (implicit val db: Database, ec: ExecutionContext, mat: Materializer) extends VersionInfo {

  import Directives._

  def routes(allowEmptyAuth: Boolean, coreClient: Core): Route =
    handleRejections(rejectionHandler) {
      ErrorHandler.handleErrors {
        pathPrefix("api" / "v1") {
          new ConfResource().route ~
          new ObjectResource(Http.extractNamespace(allowEmptyAuth)).route ~
          new RefResource(Http.extractNamespace(allowEmptyAuth), coreClient).route
        } ~ new HealthResource(db, versionMap).route
      }
    }
}
