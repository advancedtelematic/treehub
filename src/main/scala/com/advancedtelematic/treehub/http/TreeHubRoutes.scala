package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, _}
import akka.stream.Materializer
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.ErrorRepresentation
import com.advancedtelematic.libats.data.ErrorRepresentation._
import com.advancedtelematic.libats.http.{DefaultRejectionHandler, ErrorHandler}
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.slick.monitoring.DbHealthResource
import com.advancedtelematic.treehub.VersionInfo
import com.advancedtelematic.treehub.delta_store.StaticDeltaStorage
import com.advancedtelematic.treehub.object_store.ObjectStore
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter
import com.amazonaws.SdkClientException
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext

class TreeHubRoutes(namespaceExtractor: Directive1[Namespace],
                    deviceNamespace: Directive1[Namespace],
                    messageBus: MessageBusPublisher,
                    objectStore: ObjectStore,
                    deltaStorage: StaticDeltaStorage,
                    usageHandler: UsageMetricsRouter.HandlerRef)
                   (implicit val db: Database, ec: ExecutionContext, mat: Materializer) extends VersionInfo {

  import Directives._

  def allRoutes(nsExtract: Directive1[Namespace]): Route = {
    new ConfResource().route ~
    new ObjectResource(nsExtract, objectStore, usageHandler).route ~
    new RefResource(nsExtract, objectStore).route ~
    new ManifestResource(nsExtract, messageBus).route ~
    new DeltaResource(nsExtract, deltaStorage, usageHandler).route
  }

  val treehubExceptionHandler = handleExceptions(ExceptionHandler.apply {
    case ex: SdkClientException if ex.getMessage.contains("Timeout on waiting") =>
      complete(StatusCodes.RequestTimeout -> ErrorRepresentation(ErrorCodes.TimeoutOnWaiting, ex.getMessage))
  })

  val routes: Route =
    handleRejections(DefaultRejectionHandler.rejectionHandler) {
      ErrorHandler.handleErrors {
        treehubExceptionHandler {
          pathPrefix("api" / "v2") {
            allRoutes(namespaceExtractor) ~
              pathPrefix("mydevice") {
                allRoutes(deviceNamespace)
              }
          } ~
            pathPrefix("api" / "v3") {
              allRoutes(namespaceExtractor)
            } ~ DbHealthResource(versionMap).route
        }
      }
    }
}
