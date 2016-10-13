package com.advancedtelematic.ota_treehub.http

import akka.stream.Materializer
import com.advancedtelematic.ota_treehub.db.Schema
import com.advancedtelematic.ota_treehub.db.Schema.Ref
import slick.driver.MySQLDriver.api._

import scala.concurrent.ExecutionContext

class RefResource()(implicit db: Database, ec: ExecutionContext, mat: Materializer) {

  import akka.http.scaladsl.server.Directives._

  val route =
      path("refs" / Segments) { segments =>
        post {
          entity(as[String]) { value =>
            val refId = segments.mkString("/")
            val dbIO = Schema.refs.insertOrUpdate(Ref(refId, value))
            complete(db.run(dbIO).map(_ => value))
          }
        } ~
          get {
            val s = segments.mkString("/")
            val dbIO = Schema.refs.filter(_.name === s).map(_.value).take(1).result.map(_.head)
            complete(db.run(dbIO))
          }
      }
}
