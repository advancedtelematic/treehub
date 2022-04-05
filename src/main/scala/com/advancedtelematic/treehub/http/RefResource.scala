package com.advancedtelematic.treehub.http

import akka.actor.Scheduler
import akka.http.scaladsl.server.{Directive, Directive1, Route}
import akka.stream.Materializer
import com.advancedtelematic.data.DataType.{Ref, RefName}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.Commit
import com.advancedtelematic.treehub.db.RefRepository.RefNotFound
import com.advancedtelematic.treehub.db.RefRepositorySupport
import com.advancedtelematic.treehub.object_store.ObjectStore
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class RefResource(namespace: Directive1[Namespace], objectStore: ObjectStore)
                 (implicit db: Database, ec: ExecutionContext, mat: Materializer, scheduler: Scheduler)
  extends RefRepositorySupport {

  import akka.http.scaladsl.server.Directives._
  import com.advancedtelematic.libats.http.RefinedMarshallingSupport._

  private val refUpdateProcess = new RefUpdateProcess(objectStore)

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
        val f = refRepository.find(ns, refName).map(_.value.value)
        complete(f)
      }
    }
  }
}
