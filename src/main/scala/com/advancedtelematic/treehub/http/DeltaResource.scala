package com.advancedtelematic.treehub.http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directive1
import com.advancedtelematic.data.DataType.{DeltaId, ValidDeltaId}
import com.advancedtelematic.libats.data.Namespace
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter
import com.advancedtelematic.libats.data.RefinedUtils.RefineTry
import com.advancedtelematic.treehub.delta_store.StaticDeltaStorage
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter.UpdateBandwidth
import org.slf4j.LoggerFactory
import scala.util.Success

class DeltaResource(namespace: Directive1[Namespace], deltaStorage: StaticDeltaStorage, usageHandler: UsageMetricsRouter.HandlerRef) {

  import akka.http.scaladsl.server.Directives._
  import StaticDeltaStorage._

  val DeltaIdPath = Segments(2).flatMap { parts =>
    parts.mkString("/").refineTry[ValidDeltaId].toOption
  }

  val _log = LoggerFactory.getLogger(this.getClass)

  private def publishBandwidthUsage(namespace: Namespace, usageBytes: Long, deltaId: DeltaId): Unit = {
    deltaId.asObjectId match {
      case Left(err) =>
        _log.warn(s"Could not publish bandwith usage for $namespace: $err")
      case Right(objectId) =>
        usageHandler ! UpdateBandwidth(namespace, usageBytes, objectId)
    }
  }

  val route =
    extractExecutionContext { implicit ec =>
      namespace { ns =>
        path("summary") {
          _log.info(s"Getting summary for $ns")
          val f = deltaStorage.summary(ns).map { bytes =>
            HttpResponse(entity = HttpEntity(MediaTypes.`application/octet-stream`, bytes))
          }
          complete(f)
        } ~
        path("deltas" / DeltaIdPath / Segment) { (deltaId, path) =>
          val f = deltaStorage.retrieve(ns, deltaId, path)
            .andThen {
              case Success(result: StaticDeltaResponse) =>
                publishBandwidthUsage(ns, result.length, deltaId)
            }.map {
            case StaticDeltaRedirectResponse(uri, _) =>
              HttpResponse(StatusCodes.Found, headers = List(Location(uri)))
            case StaticDeltaContent(bytes, _) =>
              HttpResponse(entity = HttpEntity(MediaTypes.`application/octet-stream`, bytes))
          }

          complete(f)
        }
      }
    }
}
