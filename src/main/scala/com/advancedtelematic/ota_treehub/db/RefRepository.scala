package com.advancedtelematic.ota_treehub.db

import slick.driver.MySQLDriver.api._
import Schema._
import org.genivi.sota.http.Errors.MissingEntity

import scala.concurrent.ExecutionContext

trait RefRepositorySupport {
  def refRepository(implicit ec: ExecutionContext) = new RefRepository()
}

protected class RefRepository()(implicit ec: ExecutionContext) {
  import org.genivi.sota.db.Operators._
  import SlickAnyVal._
  import com.advancedtelematic.data.DataType._

  val RefNotFound = MissingEntity(classOf[Ref])

  def persist(ref: Ref): DBIO[Unit] = {
      Schema.refs.insertOrUpdate(ref).map(_ => ())
  }

  def find(name: RefName): DBIO[Ref] = {
    Schema.refs
      .filter(_.name === name)
      .result.failIfNotSingle(RefNotFound)
  }
}
