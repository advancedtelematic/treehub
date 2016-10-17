package com.advancedtelematic.ota_treehub.http

import akka.stream.Materializer
import com.advancedtelematic.ota_treehub.db.RefRepositorySupport
import slick.driver.MySQLDriver.api._

import scala.concurrent.ExecutionContext
import com.advancedtelematic.data.DataType.{Commit, ObjectId, Ref, RefName}


class RefResource()(implicit db: Database, ec: ExecutionContext, mat: Materializer)
  extends RefRepositorySupport
{
  import akka.http.scaladsl.server.Directives._
  import org.genivi.sota.marshalling.RefinedMarshallingSupport._

  val RefNameUri = Segments.map(s => RefName(s.mkString("/")))

  val route =
      path("refs" / RefNameUri) { refName =>
        post {
          entity(as[Commit]) { commit =>
            val dbIO = refRepository.persist(Ref(refName, commit, ObjectId.from(commit)))
            complete(db.run(dbIO).map(_ => commit.get))
          }
        } ~
          get {
            val dbIO = refRepository.find(refName).map(_.value.get)
            complete(db.run(dbIO))
          }
      }
}
