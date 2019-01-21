package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directive0, Directive1, PathMatcher1}
import akka.stream.Materializer
import com.advancedtelematic.data.DataType.ObjectId
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.treehub.object_store.BlobStore.UploadAt
import com.advancedtelematic.treehub.object_store.ObjectStore
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter.{UpdateBandwidth, UpdateStorage}
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext
import scala.util.Success

class ObjectResource(namespace: Directive1[Namespace],
                     objectStore: ObjectStore,
                     usageHandler: UsageMetricsRouter.HandlerRef)
                    (implicit db: Database, ec: ExecutionContext, mat: Materializer) {
  import akka.http.scaladsl.server.Directives._

  val PrefixedObjectId: PathMatcher1[ObjectId] = (Segment / Segment).tflatMap { case (oprefix, osuffix) =>
    ObjectId.parse(oprefix + osuffix).toOption.map(Tuple1(_))
  }

  private def hintNamespaceStorage(namespace: Namespace): Directive0 = mapResponse { resp =>
    if(resp.status.isSuccess())
      usageHandler ! UpdateStorage(namespace)
    resp
  }

  private def publishBandwidthUsage(namespace: Namespace, usageBytes: Long, objectId: ObjectId): Unit = {
    usageHandler ! UpdateBandwidth(namespace, usageBytes, objectId)
  }

  val route = namespace { ns =>
    path("objects" / PrefixedObjectId) { objectId =>
      head {
        val f = objectStore.exists(ns, objectId).map {
          case true => StatusCodes.OK
          case false => StatusCodes.NotFound
        }
        complete(f)
      } ~
      get {
        val f =
          objectStore
            .findBlob(ns, objectId)
            .andThen {
              case Success((size, _)) => publishBandwidthUsage(ns, size, objectId)
              case _ => ()
            }
            .map(_._2)

        complete(f)
      } ~
      (put & hintNamespaceStorage(ns)) {
        complete(objectStore.completeClientUpload(ns, objectId).map(_ => StatusCodes.NoContent))
      } ~
      (post & hintNamespaceStorage(ns)) {
        // TODO:SM Use storeUploadedFile and change ObjectStore api to accept File or DataBytes, when using databytes, require size, stream up
        // TODO: abstract header name/header
        // TODO: Refactor all that repeated objectstore code
        (headerValueByName("x-ats-accept-redirect") & parameter("size".as[Long])) { (_, size) =>
          onSuccess(objectStore.storeOutOfBand(ns, objectId, size)) { case UploadAt(uri) =>
            redirect(uri, StatusCodes.Found)
          }
        } ~
        fileUpload("file") { case (_, content) =>
          val f = objectStore.store(ns, objectId, content).map(_ => StatusCodes.OK)
          complete(f)
        } ~
          extractRequestEntity { entity =>
            entity.contentLengthOption match {
              case Some(size) => complete(objectStore.storeStream(ns, objectId, size, entity.dataBytes).map(_ => StatusCodes.NoContent))
              case None => reject
            }
          }
      }
    }
  }
}
