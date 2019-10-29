package com.advancedtelematic.treehub.db

import akka.http.scaladsl.server.Route
import cats.Show
import com.advancedtelematic.libats.data.DataType
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.http.Errors.{MissingEntity, MissingEntityId}
import com.advancedtelematic.libats.messaging_datatype.DataType
import com.advancedtelematic.libats.messaging_datatype.DataType.{Commit, ValidCommit}
import com.advancedtelematic.treehub.db.Schema.RevisionManifest
import eu.timepit.refined.api.Refined
import io.circe.Json
import com.advancedtelematic.libats.slick.db.SlickExtensions._
import com.advancedtelematic.libats.slick.db.SlickAnyVal._
import com.advancedtelematic.libats.slick.codecs.SlickRefined._

import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.MySQLProfile.api._

trait ManifestRepositorySupport {
  def manifestRepository(implicit db: Database, ec: ExecutionContext) = new ManifestRepository()
}

protected class ManifestRepository()(implicit db: Database, ec: ExecutionContext) {
  private implicit val showId = Show.fromToString[(Namespace, Commit)]

  def find(ns: Namespace, commit: Refined[String, ValidCommit]): Future[RevisionManifest] = db.run {
    Schema.manifests
      .filter(_.namespace === ns).filter(_.commit === commit)
      .resultHead(MissingEntityId[(Namespace, Commit)](ns -> commit))
  }

  def persist(ns: Namespace, commit: Commit, contents: Json): Future[Unit] = db.run {
    (Schema.manifests += RevisionManifest(ns, commit, contents)).map(_ => ())
  }
}
