package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directive, Directive1, Route}
import akka.stream.Materializer
import com.advancedtelematic.data.DataType.{Commit, ObjectId, Ref, RefName}
import com.advancedtelematic.treehub.db.{ObjectRepositorySupport, RefRepositorySupport}
import org.genivi.sota.data.Namespace
import slick.driver.MySQLDriver.api._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import com.advancedtelematic.treehub.db.RefRepository.RefNotFound

class RefResource(namespace: Directive1[Namespace])
                 (implicit db: Database, ec: ExecutionContext, mat: Materializer)
  extends RefRepositorySupport with ObjectRepositorySupport
{
  import akka.http.scaladsl.server.Directives._
  import org.genivi.sota.marshalling.RefinedMarshallingSupport._

  private val RefNameUri = Segments.map(s => RefName(s.mkString("/")))

  private val forcePushHeader: Directive[Tuple1[Boolean]] =
    optionalHeaderValueByName("x-ats-ostree-force").map(_.contains("true"))

  protected def onValidUpdate(ns: Namespace, oldRef: Ref, newCommit: Commit): Route = {
    val f = objectRepository.find(ns, ObjectId.from(newCommit)).map { obj =>
      oldRef.value == newCommit || RefUpdateValidation.validateParent(oldRef.value, newCommit, obj)
    }

    val newRef = Ref(ns, oldRef.name, newCommit, ObjectId.from(newCommit))

    (onSuccess(f) & forcePushHeader) { case (validParent, force) =>
      if(force || validParent) {
        complete(refRepository.persist(newRef).map(_ => newCommit.get))
      } else {
        complete(StatusCodes.PreconditionFailed -> "Cannot force push")
      }
    }
  }

  val route = namespace { ns =>
    path("refs" / RefNameUri) { refName =>
      post {
        entity(as[Commit]) { commit =>
          onComplete(refRepository.find(ns, refName)) {
            case Success(oldRef) =>
              onValidUpdate(ns, oldRef, commit)

            case Failure(RefNotFound) =>
              val f = refRepository.persist(Ref(ns, refName, commit, ObjectId.from(commit)))
              complete(f.map(_ => commit.get))

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
