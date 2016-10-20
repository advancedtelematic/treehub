package com.advancedtelematic.treehub.db

import com.advancedtelematic.data.DataType.{ObjectId, TObject}
import org.genivi.sota.http.Errors.{EntityAlreadyExists, MissingEntity}

import scala.concurrent.{ExecutionContext, Future}
import slick.driver.MySQLDriver.api._

trait ObjectRepositorySupport {
  def objectRepository(implicit db: Database, ec: ExecutionContext) = new ObjectRepository()
}

protected class ObjectRepository()(implicit db: Database, ec: ExecutionContext) {
  import org.genivi.sota.db.Operators._
  import org.genivi.sota.db.SlickExtensions._
  import SlickAnyVal._

  val ObjectNotFound = MissingEntity(classOf[TObject])
  val ObjectAlreadyExists = EntityAlreadyExists(classOf[TObject])

  def create(obj: TObject): Future[Unit] = {
    val io = (Schema.objects += obj).map(_ => ()).handleIntegrityErrors(ObjectAlreadyExists)
    db.run(io)
  }

  def findBlob(id: ObjectId): Future[Array[Byte]] =
    db.run(findAction(id).map(_.blob))

  // TODO: No namespace, will error if there is more than one
  def find(id: ObjectId): Future[TObject] = {
    db.run(findAction(id))
  }

  protected def findAction(id: ObjectId): DBIO[TObject] =
    Schema.objects
      .filter(_.id === id)
      .result.failIfNotSingle(ObjectNotFound)
}

