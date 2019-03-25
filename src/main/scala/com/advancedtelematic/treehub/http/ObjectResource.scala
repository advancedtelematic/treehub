package com.advancedtelematic.treehub.http

import java.io.File

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import akka.stream.Materializer
import com.advancedtelematic.data.DataType
import com.advancedtelematic.data.DataType.ObjectId
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.treehub.http.Errors.ObjectExists
import com.advancedtelematic.treehub.object_store.BlobStore.UploadAt
import com.advancedtelematic.treehub.object_store.ObjectStore
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter.{UpdateBandwidth, UpdateStorage}
import eu.timepit.refined.api.Refined
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

  import OutOfBandStorageHeader._

  private val outOfBandStorageEnabled = validate(objectStore.outOfBandStorageEnabled, "Out of band storage not enabled")

  def ensureNotExists(ns: Namespace, objectId: ObjectId): Directive0 = {
    Directive.Empty.tflatMap { _ =>
      val f = objectStore.exists(ns, objectId)

      onSuccess(f).flatMap {
        case false => pass
        case true => failWith(ObjectExists(objectId))
      }
    }
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
        (outOfBandStorageEnabled & headerValueByType[OutOfBandStorageHeader](()) & parameter("size".as[Long])) { (_, size) =>
          onSuccess(objectStore.storeOutOfBand(ns, objectId, size)) { case UploadAt(uri) =>
            redirect(uri, StatusCodes.Found)
          }
        } ~
        (ensureNotExists(ns, objectId) & storeUploadedFile("file", _ => File.createTempFile("http-upload", ".tmp"))) { case (_, file) =>
          val f = objectStore.storeFile(ns, objectId, file).map(_ => StatusCodes.OK)
          complete(f)
        } ~
        extractRequestEntity { entity =>
          entity.contentLengthOption match {
            case Some(size) if size > 0 => complete(objectStore.storeStream(ns, objectId, size, entity.dataBytes).map(_ => StatusCodes.NoContent))
            case _ => reject(MalformedHeaderRejection("Content-Length", "a finite length request is required to upload a file", cause = None))
          }
        }
      }
    }
  }
}
