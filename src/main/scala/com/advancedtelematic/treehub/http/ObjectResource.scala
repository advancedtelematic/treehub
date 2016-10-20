package com.advancedtelematic.treehub.http

import akka.http.scaladsl.server.{Directive1, PathMatcher1}
import akka.stream.Materializer
import com.advancedtelematic.data.DataType.{ObjectId, TObject}
import com.advancedtelematic.treehub.db.ObjectRepositorySupport
import org.genivi.sota.data.Namespace
import slick.driver.MySQLDriver.api._

import scala.concurrent.ExecutionContext


class ObjectResource(namespace: Directive1[Namespace])
                    (implicit db: Database, ec: ExecutionContext, mat: Materializer) extends ObjectRepositorySupport {
  import akka.http.scaladsl.server.Directives._

  val PrefixedObjectId: PathMatcher1[ObjectId] = (Segment / Segment).tmap { case (oprefix, osuffix) =>
    Tuple1(ObjectId(oprefix + osuffix))
  }

  val route = namespace { ns =>
    path("objects" / PrefixedObjectId) { objectId =>
      get {
        // TODO: Access control to ns
        val f = objectRepository.findBlob(objectId)
        complete(f)
      } ~
        post {
          fileUpload("file") { case (_, content) =>
            val f = for {
              (digest, content) <- ObjectUpload.readFile(content)
              _ <- objectRepository.create(TObject(ns, objectId, content.toArray))
            } yield digest

            complete(f)
          }
        }
    }
  }
}