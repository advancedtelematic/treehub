package com.advancedtelematic.treehub.db

import com.advancedtelematic.data.DataType.Ref
import com.advancedtelematic.treehub.http.Errors
import org.genivi.sota.data.Namespace
import org.genivi.sota.http.Errors.MissingEntity
import slick.driver.MySQLDriver.api._

import scala.concurrent.{ExecutionContext, Future}

trait RefRepositorySupport {
  def refRepository(implicit db: Database, ec: ExecutionContext) = new RefRepository()
}

object RefRepository {
  val RefNotFound = MissingEntity(classOf[Ref])
}

protected class RefRepository()(implicit db: Database, ec: ExecutionContext) {
  import RefRepository._
  import org.genivi.sota.db.SlickAnyVal._
  import com.advancedtelematic.data.DataType._
  import org.genivi.sota.db.SlickExtensions._

  def persist(ref: Ref): Future[Unit] =
    db.run(Schema.refs
      .insertOrUpdate(ref)
      .map(_ => ())
      .handleIntegrityErrors(Errors.CommitMissing))

  private def findQuery(namespace: Namespace, name: RefName) =
    Schema.refs
      .filter(_.name === name)
      .filter(_.namespace === namespace)

  def find(namespace: Namespace, name: RefName): Future[Ref] =
    db.run {
      findQuery(namespace, name)
        .result
        .failIfNotSingle(RefNotFound)
    }

  def setPublished(namespace: Namespace, name: RefName, published: Boolean): Future[Unit] =
    db.run {
      findQuery(namespace, name)
        .map(_.published)
        .update(published)
        .handleSingleUpdateError(RefNotFound)
    }

  def isPublished(namespace: Namespace, name: RefName): Future[Boolean] =
    db.run(findQuery(namespace, name).map(_.published).result.failIfNotSingle(RefNotFound))
}
