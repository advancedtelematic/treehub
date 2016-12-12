package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model.{HttpEntity, MediaTypes, StatusCodes}
import akka.http.scaladsl.server.{Directive1, PathMatcher1}
import akka.stream.Materializer
import com.advancedtelematic.data.DataType.ObjectId
import com.advancedtelematic.treehub.object_store.ObjectStore
import org.genivi.sota.data.Namespace
import slick.driver.MySQLDriver.api._

import scala.concurrent.ExecutionContext

class ObjectResource(namespace: Directive1[Namespace], objectStore: ObjectStore)
                    (implicit db: Database, ec: ExecutionContext, mat: Materializer) {
  import akka.http.scaladsl.server.Directives._

  val PrefixedObjectId: PathMatcher1[ObjectId] = (Segment / Segment).tmap { case (oprefix, osuffix) =>
    Tuple1(ObjectId(oprefix + osuffix))
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
      post {
        fileUpload("file") { case (_, content) =>
          val f = objectStore.store(ns, objectId, content).map(_ => StatusCodes.OK)
          complete(f)
        }
      }
    }
  }
}
