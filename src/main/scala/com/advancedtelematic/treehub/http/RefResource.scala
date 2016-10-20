package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive1
import akka.stream.Materializer
import com.advancedtelematic.data.DataType.{Commit, ObjectId, Ref, RefName}
import com.advancedtelematic.treehub.db.{ObjectRepositorySupport, RefRepositorySupport}
import org.genivi.sota.data.Namespace
import slick.driver.MySQLDriver.api._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}



class RefResource(namespace: Directive1[Namespace])
                 (implicit db: Database, ec: ExecutionContext, mat: Materializer)
  extends RefRepositorySupport with ObjectRepositorySupport
{
  import akka.http.scaladsl.server.Directives._
  import org.genivi.sota.marshalling.RefinedMarshallingSupport._

  val RefNameUri = Segments.map(s => RefName(s.mkString("/")))

  def isValidUpdate(oldCommit: Commit, newCommit: Commit): Future[Boolean] = {
    objectRepository.find(ObjectId.from(newCommit)).map { obj =>
      (oldCommit == newCommit) || RefUpdateValidation.validateParent(oldCommit, newCommit, obj)
    }
  }

  val route = namespace { ns =>
    path("refs" / RefNameUri) { refName =>
      post {
        entity(as[Commit]) { commit =>
          onComplete(refRepository.find(ns, refName)) {
            case Success(oldRef) =>
              val newRef = Ref(ns, refName, commit, ObjectId.from(commit))

              onSuccess(isValidUpdate(oldRef.value, commit)) {
                case true => complete(refRepository.persist(newRef).map(_ => commit.get))
                case false => complete(StatusCodes.PreconditionFailed -> "Cannot force push")
              }

            case Failure(_) => // TODO: not checking RefNotFound
              val f = refRepository.persist(Ref(ns, refName, commit, ObjectId.from(commit)))
              complete(f.map(_ => commit.get))
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
