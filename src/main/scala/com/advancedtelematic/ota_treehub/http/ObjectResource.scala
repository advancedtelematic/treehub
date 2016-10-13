package com.advancedtelematic.ota_treehub.http

import akka.http.scaladsl.model.StatusCodes
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink}
import akka.util.ByteString
import com.advancedtelematic.common.DigestCalculator
import com.advancedtelematic.ota_treehub.db.Schema
import com.advancedtelematic.ota_treehub.db.Schema.{Ref, TObject}
import slick.driver.MySQLDriver.api._

import scala.concurrent.ExecutionContext

class ObjectResource()(implicit db: Database, ec: ExecutionContext, mat: Materializer) {

  import akka.http.scaladsl.server.Directives._

  val route =
      path("objects" / Segment / Segment) { (oprefix, osuffix) =>
        get {
          val objectId = s"$oprefix$osuffix"
          val dbIO = Schema.objects.filter(_.id === objectId).take(1).map(_.blob).result.map(_.headOption)

          val f = db.run(dbIO)

          onSuccess(f) {
            case Some(o) => complete(o)
            case None => complete(StatusCodes.NotFound -> s"object $objectId not found")
          }
        } ~
          post {
            val objectId = s"$oprefix$osuffix"
            fileUpload("file") { case (fileInfo, content) =>
              val digestCalculator = DigestCalculator()

              val (digestF, contentF) =
                content
                  .alsoToMat(digestCalculator)(Keep.right)
                  .toMat(Sink.reduce[ByteString]((a: ByteString, b: ByteString) => a ++ b))(Keep.both)
                  .run()

              val f = for {
                by <- contentF
                d <- digestF
                _ <- db.run(Schema.objects.insertOrUpdate(TObject(objectId, by.toArray)))
              } yield d

              complete(f)
            }
          }
      }
}
