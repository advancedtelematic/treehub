package com.advancedtelematic.ota_treehub.http

import akka.http.scaladsl.server.Directive1
import akka.stream.Materializer
import com.advancedtelematic.ota_treehub.db.RefRepositorySupport
import slick.driver.MySQLDriver.api._

import scala.concurrent.ExecutionContext
import com.advancedtelematic.data.DataType.{Commit, ObjectId, Ref, RefName}
import org.genivi.sota.data.Namespace


class RefResource(namespace: Directive1[Namespace])
                 (implicit db: Database, ec: ExecutionContext, mat: Materializer)
  extends RefRepositorySupport
{
  import akka.http.scaladsl.server.Directives._
  import org.genivi.sota.marshalling.RefinedMarshallingSupport._

  val RefNameUri = Segments.map(s => RefName(s.mkString("/")))

  val route = namespace { ns =>
    path("refs" / RefNameUri) { refName =>
      post {
        entity(as[Commit]) { commit =>
          val dbIO = refRepository.persist(Ref(ns, refName, commit, ObjectId.from(commit)))
          complete(db.run(dbIO).map(_ => commit.get))
        }
      } ~
        get {
          val dbIO = refRepository.find(refName).map(_.value.get)
          complete(db.run(dbIO))
        }
    }
  }
}
