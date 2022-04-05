package com.advancedtelematic.treehub.db

import akka.actor.Scheduler
import cats.Show
import com.advancedtelematic.data.DataType.{CommitManifest, ObjectId}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.http.Errors.MissingEntityId
import com.advancedtelematic.libats.slick.codecs.SlickRefined._
import com.advancedtelematic.libats.slick.db.DatabaseHelper.DatabaseWithRetry
import com.advancedtelematic.libats.slick.db.SlickAnyVal._
import com.advancedtelematic.libats.slick.db.SlickExtensions._
import com.advancedtelematic.treehub.http.Errors
import io.circe.Json
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

trait ManifestRepositorySupport {
  def manifestRepository(implicit db: Database, ec: ExecutionContext, scheduler: Scheduler) = new ManifestRepository()
}


protected class ManifestRepository()(implicit db: Database, ec: ExecutionContext, scheduler: Scheduler) {
  private implicit val showId = Show.fromToString[(Namespace, ObjectId)]

  def find(ns: Namespace, objectId: ObjectId): Future[CommitManifest] = db.runWithRetry {
    Schema.manifests
      .filter(_.namespace === ns).filter(_.commit === objectId)
      .resultHead(MissingEntityId[(Namespace, ObjectId)](ns -> objectId))
  }

  def persist(ns: Namespace, commit: ObjectId, contents: Json): Future[Unit] = db.runWithRetry {
    Schema.manifests
      .insertOrUpdate(CommitManifest(ns, commit, contents))
      .handleIntegrityErrors(Errors.CommitMissing)
      .map(_ => ())
  }

  def deleteByNamespace(namespace: Namespace): Future[Int] = db.runWithRetry {
    Schema.manifests
      .filter(_.namespace === namespace)
      .delete
  }
}
