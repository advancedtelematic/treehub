package com.advancedtelematic.treehub.http

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.data.DataType.{ObjectId, Ref, RefName, _}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.treehub.db.RefRepositorySupport
import com.advancedtelematic.treehub.object_store.ObjectStore
import slick.jdbc.MySQLProfile.api._
import com.advancedtelematic.libats.codecs.CirceCodecs._
import com.advancedtelematic.libats.messaging_datatype.DataType.Commit
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import scala.concurrent.{ExecutionContext, Future}

class RefUpdateProcess(objectStore: ObjectStore)(implicit db: Database, ec: ExecutionContext)
  extends RefRepositorySupport {

  def update(ns: Namespace, ref: Ref, commit: Commit, forcePush: Boolean): Future[ToResponseMarshallable] = {
    commitIsValidParent(ns, ref, commit).flatMap { validParent =>
      if(forcePush || validParent) {
        val newRef = ref.copy(value = commit, objectId = ObjectId.from(commit))

        refRepository
          .persist(newRef)
          .map(_ => StatusCodes.OK -> commit.value)
      } else {
        Future.successful(StatusCodes.PreconditionFailed -> "Cannot force push")
      }
    }
  }

  def create(ns: Namespace, refName: RefName, commit: Commit): Future[ToResponseMarshallable] = {
    val newRef = Ref(ns, refName, commit, ObjectId.from(commit))

    refRepository
      .persist(newRef)
      .map(_ => commit.value)
  }

  private def commitIsValidParent(ns: Namespace, ref: Ref, newCommit: Commit): Future[Boolean] = {
    objectStore.readFull(ns, ObjectId.from(newCommit)).map { blob =>
      ref.value == newCommit || RefUpdateValidation.validateParent(ref.value, newCommit, blob)
    }
  }
}
