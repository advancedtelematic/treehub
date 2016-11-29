package com.advancedtelematic.treehub.http

import akka.http.scaladsl.server.{Directive, Directive1, Route}
import akka.stream.Materializer
import com.advancedtelematic.data.DataType.{Commit, Ref, RefName}
import com.advancedtelematic.treehub.client.Core
import com.advancedtelematic.treehub.db.RefRepository.RefNotFound
import com.advancedtelematic.treehub.db.{ObjectRepositorySupport, RefRepositorySupport}
import org.genivi.sota.data.Namespace
import slick.driver.MySQLDriver.api._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class RefResource(namespace: Directive1[Namespace], coreClient: Core)
                 (implicit db: Database, ec: ExecutionContext, mat: Materializer)
  extends RefRepositorySupport with ObjectRepositorySupport {

  import akka.http.scaladsl.server.Directives._
  import org.genivi.sota.marshalling.RefinedMarshallingSupport._

  private val refUpdateProcess = new RefUpdateProcess(coreClient)

  private val RefNameUri = Segments.map(s => RefName(s.mkString("/")))

  private val forcePushHeader: Directive[Tuple1[Boolean]] =
    optionalHeaderValueByName("x-ats-ostree-force").map(_.contains("true"))

  protected def updateRef(ns: Namespace, oldRef: Ref, newCommit: Commit): Route = {
    forcePushHeader { forcePush =>
      complete(refUpdateProcess.update(ns, oldRef, newCommit, forcePush))
    }
  }

  val route = namespace { ns =>
    path("refs" / RefNameUri) { refName =>
      post {
        entity(as[Commit]) { commit =>
          onComplete(refRepository.find(ns, refName)) {
            case Success(oldRef) =>
              updateRef(ns, oldRef, commit)

            case Failure(RefNotFound) =>
              val f = refUpdateProcess.create(ns, refName, commit)
              complete(f)

            case Failure(err) => failWith(err)
          }
        }
      } ~
      get {
        val f = refRepository.find(ns, refName).map(_.value.get)
        complete(f)
      }
    }
  }
}
