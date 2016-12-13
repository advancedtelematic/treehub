package com.advancedtelematic.treehub.db

import com.advancedtelematic.data.DataType.{ObjectId, TObject}
import com.advancedtelematic.treehub.db.Schema.TObjectTable
import com.advancedtelematic.treehub.http.Errors
import org.genivi.sota.data.Namespace
import org.genivi.sota.http.Errors.{EntityAlreadyExists, MissingEntity}

import scala.concurrent.{ExecutionContext, Future}
import slick.driver.MySQLDriver.api._

trait ObjectRepositorySupport {
  def objectRepository(implicit db: Database, ec: ExecutionContext) = new ObjectRepository()
}

protected class ObjectRepository()(implicit db: Database, ec: ExecutionContext) {
  import org.genivi.sota.db.Operators._
  import org.genivi.sota.db.SlickExtensions._
  import org.genivi.sota.db.SlickAnyVal._

  def create(obj: TObject): Future[TObject] = {
    val io = (Schema.objects += obj).map(_ => obj).handleIntegrityErrors(Errors.ObjectExists)
    db.run(io)
  }

  def exists(namespace: Namespace, id: ObjectId): Future[Boolean] =
    db.run(findQuery(namespace, id).exists.result)

  def find(namespace: Namespace, id: ObjectId): Future[TObject] = {
    db.run(findAction(namespace, id))
  }

  private def findQuery(namespace: Namespace, id: ObjectId): Query[TObjectTable, TObject, Seq] =
    Schema.objects
      .filter(_.id === id).filter(_.namespace === namespace)

  private def findAction(namespace: Namespace, id: ObjectId): DBIO[TObject] =
    findQuery(namespace, id)
      .result.failIfNotSingle(Errors.ObjectNotFound)
}

