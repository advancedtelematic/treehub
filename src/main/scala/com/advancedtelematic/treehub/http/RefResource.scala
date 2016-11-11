package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directive, Directive1, Route}
import akka.stream.Materializer
import com.advancedtelematic.data.DataType.{Commit, ObjectId, Ref, RefName}
import com.advancedtelematic.treehub.db.RefRepository.RefNotFound
import com.advancedtelematic.treehub.db.{ObjectRepositorySupport, RefRepositorySupport}
import org.genivi.sota.data.Namespace
import slick.driver.MySQLDriver.api._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class RefResource(namespace: Directive1[Namespace], coreClient: Core)
                 (implicit db: Database, ec: ExecutionContext, mat: Materializer)
  extends RefRepositorySupport with ObjectRepositorySupport {

  import akka.http.scaladsl.server.Directives._
  import org.genivi.sota.marshalling.RefinedMarshallingSupport._

  private val RefNameUri = Segments.map(s => RefName(s.mkString("/")))

  private val forcePushHeader: Directive[Tuple1[Boolean]] =
    optionalHeaderValueByName("x-ats-ostree-force").map(_.contains("true"))

  private def storeCommitInCore(ref: Ref): Future[Unit] = {
    //TODO: PRO-1802 pass the refname as the description until we can parse the real description out of the commit
    coreClient.storeCommitInCore(ref.namespace, ref.value, ref.name, ref.name.get)
      .flatMap(_ => refRepository.setSavedInCore(ref.namespace, ref.name, savedInCore = true))
  }

  protected def onValidUpdate(ns: Namespace, oldRef: Ref, newCommit: Commit): Route = {
    val f = objectRepository.find(ns, ObjectId.from(newCommit)).map { obj =>
      oldRef.value == newCommit || RefUpdateValidation.validateParent(oldRef.value, newCommit, obj)
    }

    (onSuccess(f) & forcePushHeader) { case (validParent, force) =>
      if(force || validParent) {
        val newRef = Ref(ns, oldRef.name, newCommit, ObjectId.from(newCommit))
        val f = refRepository.persist(newRef).map(_ => newCommit.get)
        if(oldRef.value != newCommit || !oldRef.savedInCore) {
          f.flatMap(_ => storeCommitInCore(newRef))
        }
        complete(f)
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
              val newRef = Ref(ns, refName, commit, ObjectId.from(commit))
              val f = refRepository.persist(newRef)
              f.flatMap(_ => storeCommitInCore(newRef))
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
