package com.advancedtelematic.ota_treehub.http

import akka.stream.Materializer
import com.advancedtelematic.ota_treehub.db.{RefRepositorySupport, Schema}
import com.advancedtelematic.ota_treehub.db.Schema.Ref
import slick.driver.MySQLDriver.api._

import scala.concurrent.ExecutionContext
import Schema.RefName
import com.advancedtelematic.data.DataType.Commit


class RefResource()(implicit db: Database, ec: ExecutionContext, mat: Materializer)
  extends RefRepositorySupport
{
  import akka.http.scaladsl.server.Directives._
  import org.genivi.sota.marshalling.RefinedMarshallingSupport._

  val RefNameUri = Segments.map(s => RefName(s.mkString("/")))

  val route =
      path("refs" / RefNameUri) { refName =>
        post {
          entity(as[Commit]) { value =>
            val dbIO = refRepository.persist(Ref(refName, value))
            complete(db.run(dbIO).map(_ => value.get))
          }
        } ~
          get {
            val dbIO = refRepository.find(refName).map(_.value.get)
            complete(db.run(dbIO))
          }
      }
}
