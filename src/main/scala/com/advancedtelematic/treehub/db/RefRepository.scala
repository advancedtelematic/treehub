package com.advancedtelematic.treehub.db

import akka.actor.Scheduler
import com.advancedtelematic.data.DataType.Ref
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.http.Errors.MissingEntity
import com.advancedtelematic.libats.slick.db.DatabaseHelper.DatabaseWithRetry
import com.advancedtelematic.treehub.http.Errors
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

trait RefRepositorySupport {
  def refRepository(implicit db: Database, ec: ExecutionContext, scheduler: Scheduler) = new RefRepository()
}

object RefRepository {
  val RefNotFound = MissingEntity[Ref]()
}

protected class RefRepository()(implicit db: Database, ec: ExecutionContext, scheduler: Scheduler) {
  import RefRepository._
  import com.advancedtelematic.libats.slick.db.SlickAnyVal._
  import com.advancedtelematic.data.DataType._
  import com.advancedtelematic.libats.slick.db.SlickExtensions._

  def persist(ref: Ref): Future[Unit] =
    db.runWithRetry(Schema.refs
      .insertOrUpdate(ref)
      .map(_ => ())
      .handleIntegrityErrors(Errors.CommitMissing))

  private def findQuery(namespace: Namespace, name: RefName) =
    Schema.refs
      .filter(_.name === name)
      .filter(_.namespace === namespace)

  def find(namespace: Namespace, name: RefName): Future[Ref] =
    db.runWithRetry {
      findQuery(namespace, name)
        .result
        .failIfNotSingle(RefNotFound)
    }

  def deleteByNamespace(namespace: Namespace): Future[Int] = {
    db.runWithRetry {
      Schema.refs
        .filter(_.namespace === namespace)
        .delete
    }
  }
}
