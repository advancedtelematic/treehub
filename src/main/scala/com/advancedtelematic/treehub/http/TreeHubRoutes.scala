package com.advancedtelematic.treehub.http

import akka.actor.ActorRef
import akka.http.scaladsl.server.{Directives, _}
import akka.stream.Materializer
import org.genivi.sota.data.Namespace
import com.advancedtelematic.treehub.VersionInfo
import com.advancedtelematic.treehub.client.Core
import com.advancedtelematic.treehub.object_store.ObjectStore
import org.genivi.sota.http.{ErrorHandler, HealthResource}
import org.genivi.sota.rest.SotaRejectionHandler._

import scala.concurrent.ExecutionContext
import slick.driver.MySQLDriver.api._


class TreeHubRoutes(tokenValidator: Directive0,
                    namespaceExtractor: Directive1[Namespace],
                    coreClient: Core,
                    deviceNamespace: Directive1[Namespace],
                    objectStore: ObjectStore,
                    storageHandler: ActorRef
                   )
                   (implicit val db: Database, ec: ExecutionContext, mat: Materializer) extends VersionInfo {

  import Directives._

  def allRoutes(nsExtract: Directive1[Namespace]): Route = {
    new ConfResource().route ~
    new ObjectResource(nsExtract, objectStore, storageHandler).route ~
    new RefResource(nsExtract, coreClient, objectStore).route
  }

  val routes: Route =
    handleRejections(rejectionHandler) {
      ErrorHandler.handleErrors {
        (pathPrefix("api" / "v2") & tokenValidator) {
            allRoutes(namespaceExtractor) ~
            pathPrefix("mydevice") {
              allRoutes(deviceNamespace)
            }
        } ~ new HealthResource(db, versionMap).route
      }
    }
}
