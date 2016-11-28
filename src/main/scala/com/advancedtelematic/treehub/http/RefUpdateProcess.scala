package com.advancedtelematic.treehub.http

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.data.DataType.{ObjectId, Ref, RefName, _}
import com.advancedtelematic.treehub.client.Core
import com.advancedtelematic.treehub.db.{ObjectRepositorySupport, RefRepositorySupport}
import org.genivi.sota.data.Namespace
import org.slf4j.LoggerFactory
import slick.driver.MySQLDriver.api._

import scala.concurrent.{ExecutionContext, Future}

class RefUpdateProcess(coreClient: Core)(implicit db: Database, ec: ExecutionContext)
  extends ObjectRepositorySupport with RefRepositorySupport {

  private val _log = LoggerFactory.getLogger(this.getClass)

  def update(ns: Namespace, ref: Ref, newCommit: Commit, forcePush: Boolean): Future[ToResponseMarshallable] = {
    validParentFn(ns, ref, newCommit).flatMap { validParent =>
      if(forcePush || validParent) {
        val newRef = ref.copy(value = newCommit, objectId = ObjectId.from(newCommit))

        for {
          _ <- refRepository.persist(newRef)
          _ <- publishIfPending(ref, newRef)
        } yield StatusCodes.OK -> newCommit.get

      } else {
        Future.successful(StatusCodes.PreconditionFailed -> "Cannot force push")
      }
    }
  }

  def create(ns: Namespace, refName: RefName, commit: Commit): Future[ToResponseMarshallable] = {
    val newRef = Ref(ns, refName, commit, ObjectId.from(commit))

    for {
      _ <- refRepository.persist(newRef)
      _ <- publishRef(newRef)
    } yield commit.get
  }

  private def validParentFn(ns: Namespace, ref: Ref, newCommit: Commit): Future[Boolean] = {
    objectRepository.find(ns, ObjectId.from(newCommit)).map { obj =>
      ref.value == newCommit || RefUpdateValidation.validateParent(ref.value, newCommit, obj)
    }
  }

  private def publishIfPending(ref: Ref, newRef: Ref): Future[Unit] = {
    if(ref.value != newRef.value || !ref.savedInCore) {
      publishRef(newRef)
    } else
      Future.successful(())
  }

  private def publishRef(ref: Ref): Future[Unit] = {
    //TODO: PRO-1802 pass the refname as the description until we can parse the real description out of the commit
    coreClient
      .publishRef(ref, ref.value.get)
      .flatMap(_ => refRepository.setSavedInCore(ref.namespace, ref.name, savedInCore = true))
      .recover { case err =>
        _log.error("Could not publish ref to core", err)
      }
  }
}
