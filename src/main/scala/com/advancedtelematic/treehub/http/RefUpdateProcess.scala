package com.advancedtelematic.treehub.http

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.data.DataType.{ObjectId, Ref, RefName, _}
import com.advancedtelematic.libats.data.Namespace
import com.advancedtelematic.treehub.client.Core
import com.advancedtelematic.treehub.db.RefRepositorySupport
import com.advancedtelematic.treehub.object_store.ObjectStore
import org.slf4j.LoggerFactory
import slick.driver.MySQLDriver.api._
import com.advancedtelematic.libats.codecs.AkkaCirce._
import com.advancedtelematic.libats.messaging_datatype.DataType.Commit
import de.heikoseeberger.akkahttpcirce.CirceSupport._

import scala.concurrent.{ExecutionContext, Future}

class RefUpdateProcess(coreClient: Core, objectStore: ObjectStore)(implicit db: Database, ec: ExecutionContext)
  extends RefRepositorySupport {

  private val _log = LoggerFactory.getLogger(this.getClass)

  def update(ns: Namespace, ref: Ref, commit: Commit, forcePush: Boolean): Future[ToResponseMarshallable] = {
    commitIsValidParent(ns, ref, commit).flatMap { validParent =>
      if(forcePush || validParent) {
        val newRef = ref.copy(value = commit, objectId = ObjectId.from(commit))

        for {
          _ <- refRepository.persist(newRef)
          _ <- publishIfPending(ref, newRef)
        } yield StatusCodes.OK -> commit.get

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

  private def commitIsValidParent(ns: Namespace, ref: Ref, newCommit: Commit): Future[Boolean] = {
    objectStore.readFull(ns, ObjectId.from(newCommit)).map { blob =>
      ref.value == newCommit || RefUpdateValidation.validateParent(ref.value, newCommit, blob)
    }
  }

  private def publishIfPending(ref: Ref, newRef: Ref): Future[Unit] = {
    refRepository.isPublished(ref.namespace, ref.name).flatMap { published =>
      if (ref.value != newRef.value || !published) {
        publishRef(newRef)
      } else
        Future.successful(())
    }
  }

  private def publishRef(ref: Ref): Future[Unit] = {
    //TODO: PRO-1802 pass the refname as the description until we can parse the real description out of the commit
    coreClient
      .publishRef(ref, ref.value.get)
      .flatMap(_ => refRepository.setPublished(ref.namespace, ref.name, published = true))
      .recover { case err =>
        _log.error("Could not publish ref to core", err)
      }
  }
}
