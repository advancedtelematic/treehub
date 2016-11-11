package com.advancedtelematic.treehub.http

import akka.http.scaladsl.server.{Directives, _}
import akka.stream.Materializer
import org.genivi.sota.data.Namespace
import com.advancedtelematic.treehub.VersionInfo
import org.genivi.sota.http.{ErrorHandler, HealthResource}
import org.genivi.sota.rest.SotaRejectionHandler._

import scala.concurrent.ExecutionContext
import slick.driver.MySQLDriver.api._


class TreeHubRoutes(tokenValidator: String => Directive0,
                    namespaceExtractor : String => Directive1[Namespace],
                    coreClient: Core)
                   (implicit val db: Database, ec: ExecutionContext, mat: Materializer) extends VersionInfo {

  import Directives._

  def matchVersion(path: String): PathMatcher1[String] = path.tmap{ _ => Tuple1(path)}

  def versionExtractor: PathMatcher1[String] = matchVersion("v1") | matchVersion("v2")

  val routes: Route =
    handleRejections(rejectionHandler) {
      ErrorHandler.handleErrors {
        pathPrefix("api" / versionExtractor) { apiVersion =>
          tokenValidator(apiVersion) {
            new ConfResource().route ~
            new ObjectResource(namespaceExtractor(apiVersion)).route ~
            new RefResource(namespaceExtractor(apiVersion), coreClient).route
          }
        } ~ new HealthResource(db, versionMap).route
      }
    }
}
