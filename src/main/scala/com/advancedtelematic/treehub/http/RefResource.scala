package com.advancedtelematic.treehub.http

import akka.http.scaladsl.server.Directive1
import akka.stream.Materializer
import slick.driver.MySQLDriver.api._

import scala.concurrent.ExecutionContext
import com.advancedtelematic.data.DataType.{Commit, ObjectId, Ref, RefName}
import com.advancedtelematic.treehub.db.RefRepositorySupport
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
          val f = refRepository.persist(Ref(ns, refName, commit, ObjectId.from(commit)))
          complete(f.map(_ => commit.get))
        }
      } ~
        get {
          val f = refRepository.find(ns, refName).map(_.value.get)
          complete(f)
        }
    }
  }
}
