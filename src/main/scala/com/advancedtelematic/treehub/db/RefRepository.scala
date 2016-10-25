package com.advancedtelematic.treehub.db

import com.advancedtelematic.data.DataType.Ref
import com.advancedtelematic.treehub.http.Errors
import org.genivi.sota.data.Namespace
import slick.driver.MySQLDriver.api._
import org.genivi.sota.http.Errors.MissingEntity

import scala.concurrent.{ExecutionContext, Future}

trait RefRepositorySupport {
  def refRepository(implicit db: Database, ec: ExecutionContext) = new RefRepository()
}

protected class RefRepository()(implicit db: Database, ec: ExecutionContext) {
  import org.genivi.sota.db.Operators._
  import SlickAnyVal._
  import com.advancedtelematic.data.DataType._
  import org.genivi.sota.db.SlickExtensions._

  val RefNotFound = MissingEntity(classOf[Ref])

  def persist(ref: Ref): Future[Unit] = {
    val dbIO = Schema.refs.insertOrUpdate(ref).map(_ => ()).handleIntegrityErrors(Errors.CommitMissing)
    db.run(dbIO)
  }

  def find(namespace: Namespace, name: RefName): Future[Ref] = {
    val dbIO = Schema.refs
      .filter(_.name === name).filter(_.namespace === namespace)
      .result.failIfNotSingle(RefNotFound)
    db.run(dbIO)
  }
}
