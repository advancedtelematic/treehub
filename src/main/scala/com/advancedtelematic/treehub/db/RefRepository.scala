package com.advancedtelematic.treehub.db

import com.advancedtelematic.data.DataType.Ref
import com.advancedtelematic.treehub.http.{CoreClient, Errors}
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
  import org.genivi.sota.db.Operators._
  import org.genivi.sota.db.SlickAnyVal._
  import com.advancedtelematic.data.DataType._
  import org.genivi.sota.db.SlickExtensions._
  import RefRepository._

  def persist(ref: Ref): Future[Unit] = {
    val dbIO = Schema.refs.insertOrUpdate(ref).map(_ => ()).handleIntegrityErrors(Errors.CommitMissing)
    db.run(dbIO)
  }

  protected[db] def findQuery(namespace: Namespace, name: RefName): DBIO[Ref] =
    Schema.refs
      .filter(_.name === name).filter(_.namespace === namespace)
      .result.failIfNotSingle(RefNotFound)

  def find(namespace: Namespace, name: RefName): Future[Ref] = {
    db.run(findQuery(namespace, name))
  }

  def setSavedInCore(namespace: Namespace, name: RefName, savedInCore: Boolean): Future[Unit] = {
    db.run(Schema.refs
      .filter(_.name === name).filter(_.namespace === namespace)
      .map(_.savedInCore)
      .update(savedInCore)
      .handleSingleUpdateError(RefNotFound))
  }

  def isSavedInCore(namespace: Namespace, name: RefName): Future[Boolean] =
    db.run(findQuery(namespace, name).map(_.savedInCore))
}
