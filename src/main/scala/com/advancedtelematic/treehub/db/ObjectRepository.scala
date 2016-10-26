package com.advancedtelematic.treehub.db

import com.advancedtelematic.data.DataType.{ObjectId, TObject}
import org.genivi.sota.data.Namespace
import org.genivi.sota.http.Errors.{EntityAlreadyExists, MissingEntity}

import scala.concurrent.{ExecutionContext, Future}
import slick.driver.MySQLDriver.api._

trait ObjectRepositorySupport {
  def objectRepository(implicit db: Database, ec: ExecutionContext) = new ObjectRepository()
}

object ObjectRepository {
  val ObjectNotFound = MissingEntity(classOf[TObject])
  val ObjectAlreadyExists = EntityAlreadyExists(classOf[TObject])
}

protected class ObjectRepository()(implicit db: Database, ec: ExecutionContext) {
  import org.genivi.sota.db.Operators._
  import org.genivi.sota.db.SlickExtensions._
  import SlickAnyVal._
  import ObjectRepository._

  def create(obj: TObject): Future[TObject] = {
    val io = (Schema.objects += obj).map(_ => obj).handleIntegrityErrors(ObjectAlreadyExists)
    db.run(io)
  }

  def findBlob(namespace: Namespace, id: ObjectId): Future[Array[Byte]] =
    db.run(findAction(namespace, id).map(_.blob))

  // TODO: No namespace, will error if there is more than one
  def find(namespace: Namespace, id: ObjectId): Future[TObject] = {
    db.run(findAction(namespace, id))
  }

  protected def findAction(namespace: Namespace, id: ObjectId): DBIO[TObject] =
    Schema.objects
      .filter(_.id === id).filter(_.namespace === namespace)
      .result.failIfNotSingle(ObjectNotFound)
}

