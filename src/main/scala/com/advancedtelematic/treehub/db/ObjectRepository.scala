package com.advancedtelematic.treehub.db

import com.advancedtelematic.data.DataType.{ObjectId, TObject}
import org.genivi.sota.http.Errors.{EntityAlreadyExists, MissingEntity}

import scala.concurrent.ExecutionContext
import slick.driver.MySQLDriver.api._

trait ObjectRepositorySupport {
  def objectRepository(implicit ec: ExecutionContext) = new ObjectRepository()
}

protected class ObjectRepository()(implicit ec: ExecutionContext) {
  import org.genivi.sota.db.Operators._
  import org.genivi.sota.db.SlickExtensions._
  import SlickAnyVal._

  val ObjectNotFound = MissingEntity(classOf[TObject])
  val ObjectAlreadyExists = EntityAlreadyExists(classOf[TObject])

  def create(obj: TObject): DBIO[Unit] = {
    (Schema.objects += obj).map(_ => ()).handleIntegrityErrors(ObjectAlreadyExists)
  }

  def findBlob(id: ObjectId): DBIO[Array[Byte]] =
    find(id).map(_.blob)

  // TODO: No namespace, will error if there is more than one
  def find(id: ObjectId): DBIO[TObject] = {
    Schema.objects
      .filter(_.id === id)
      .result.failIfNotSingle(ObjectNotFound)
  }
}

