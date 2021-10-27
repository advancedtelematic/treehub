package com.advancedtelematic.treehub.daemon

import akka.Done
import com.advancedtelematic.libats.messaging_datatype.Messages.OSTreeTargetDelete
import com.advancedtelematic.treehub.db.{ManifestRepositorySupport, RefRepositorySupport}
import com.advancedtelematic.treehub.http.Errors
import com.advancedtelematic.treehub.object_store.ObjectStore
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

object OSTreeTargetDeleteListener extends RefRepositorySupport with ManifestRepositorySupport {
  private val log = LoggerFactory.getLogger(this.getClass)

    def delete(objectStore: ObjectStore)(osTreeDelete: OSTreeTargetDelete)(implicit db: Database, ec: ExecutionContext): Future[Done] = {
      log.info(s"Removing osTree images for namespace ${osTreeDelete.namespace}")
      for {
        _ <- refRepository.deleteByNamespace(osTreeDelete.namespace)
        _ <- manifestRepository.deleteByNamespace(osTreeDelete.namespace)
        _ <- objectStore.deleteByNamespace(osTreeDelete.namespace)
      } yield Done
    }.recover {
      case Errors.ObjectNotFound =>
        log.error(s"Objects for namespace ${osTreeDelete.namespace} not found")
        Done
    }
}
