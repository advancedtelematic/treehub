package com.advancedtelematic.ota_treehub.http

import akka.http.scaladsl.server.PathMatcher1
import akka.stream.Materializer
import com.advancedtelematic.data.DataType.{ObjectId, TObject}
import com.advancedtelematic.ota_treehub.db.ObjectRepositorySupport
import slick.driver.MySQLDriver.api._

import scala.concurrent.ExecutionContext


class ObjectResource()(implicit db: Database, ec: ExecutionContext, mat: Materializer) extends ObjectRepositorySupport {
  import akka.http.scaladsl.server.Directives._

  val PrefixedObjectId: PathMatcher1[ObjectId] = (Segment / Segment).tmap { case (oprefix, osuffix) =>
    Tuple1(ObjectId(oprefix + osuffix))
  }

  val route =
      path("objects" / PrefixedObjectId) { objectId =>
        get {
          val dbIO = objectRepository.findBlob(objectId)
          val f = db.run(dbIO)
          complete(f)
        } ~
          post {
            fileUpload("file") { case (_, content) =>
              val f = for {
                (digest, content) <- ObjectUpload.readFile(content)
                _ <- db.run(objectRepository.create(TObject(objectId, content.toArray)))
              } yield digest

              complete(f)
            }
          }
      }
}
