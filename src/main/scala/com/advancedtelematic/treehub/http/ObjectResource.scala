package com.advancedtelematic.treehub.http

import akka.actor.ActorRef
import akka.http.scaladsl.model.{HttpEntity, MediaTypes, StatusCodes}
import akka.http.scaladsl.server.{Directive0, Directive1, PathMatcher1}
import akka.stream.Materializer
import com.advancedtelematic.data.DataType.ObjectId
import com.advancedtelematic.treehub.object_store.ObjectStore
import com.advancedtelematic.treehub.repo_metrics.StorageUpdate.Update
import org.genivi.sota.data.Namespace
import slick.driver.MySQLDriver.api._

import scala.concurrent.ExecutionContext

class ObjectResource(namespace: Directive1[Namespace], objectStore: ObjectStore, storageHandler: ActorRef)
                    (implicit db: Database, ec: ExecutionContext, mat: Materializer) {
  import akka.http.scaladsl.server.Directives._

  val PrefixedObjectId: PathMatcher1[ObjectId] = (Segment / Segment).tmap { case (oprefix, osuffix) =>
    Tuple1(ObjectId(oprefix + osuffix))
  }

  private def hintNamespaceStorage(namespace: Namespace): Directive0 = mapResponse { resp =>
    if(resp.status.isSuccess())
      storageHandler ! Update(namespace)
    resp
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
        val f = objectStore.findBlob(ns, objectId).map(HttpEntity(MediaTypes.`application/octet-stream`, _))
        complete(f)
      } ~
      (post & hintNamespaceStorage(ns)) {
        fileUpload("file") { case (_, content) =>
          val f = objectStore.store(ns, objectId, content).map(_ => StatusCodes.OK)
          complete(f)
        }
      }
    }
  }
}
