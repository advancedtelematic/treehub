package com.advancedtelematic.treehub.db

import com.advancedtelematic.data.DataType.{ObjectId, TObject}
import com.advancedtelematic.treehub.db.Schema.TObjectTable
import com.advancedtelematic.treehub.http.Errors
import org.genivi.sota.data.Namespace

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

  def usage(namespace: Namespace): Future[Long] =
    db.run(Schema.objects.filter(_.namespace === namespace).map(_.size).sum.result.map(_.getOrElse(0L)))

  private def findQuery(namespace: Namespace, id: ObjectId): Query[TObjectTable, TObject, Seq] =
    Schema.objects
      .filter(_.id === id).filter(_.namespace === namespace)

  private def findAction(namespace: Namespace, id: ObjectId): DBIO[TObject] =
    findQuery(namespace, id)
      .result.failIfNotSingle(Errors.ObjectNotFound)
}

trait StorageUsageStateUpdateSupport {
  def storageUsageState(implicit db: Database, ec: ExecutionContext) = new StorageUsageStateUpdate()
}

protected[db] class StorageUsageStateUpdate()(implicit db: Database, ec: ExecutionContext) {
  import Schema._
  import org.genivi.sota.db.Operators._
  import org.genivi.sota.db.SlickExtensions._
  import org.genivi.sota.db.SlickAnyVal._

  def isOutdated(namespace: Namespace): Future[Boolean] =
    db.run(Schema.objects.filter(_.namespace === namespace).filter(_.size === 0L).map(_.size).exists.result)

  def update(namespace: Namespace, usages: Map[ObjectId, Long]): Future[Long] = {
    val actions = usages.foldLeft(List.empty[DBIO[Long]]) { case (acc, (oid, usage)) ⇒
      Schema.objects
        .filter(_.namespace === namespace)
        .filter(_.id === oid)
        .map(_.size)
        .update(usage)
        .map(_ ⇒ usage) :: acc
    }

    db.run(DBIO.sequence(actions).map(_.sum))
  }
}
