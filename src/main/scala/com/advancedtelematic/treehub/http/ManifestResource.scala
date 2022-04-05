package com.advancedtelematic.treehub.http

import akka.actor.Scheduler

import java.time.Instant
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive1
import akka.stream.Materializer
import com.advancedtelematic.data.DataType.ObjectId
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType.ValidCommit
import com.advancedtelematic.libats.messaging_datatype.Messages.CommitManifestUpdated
import com.advancedtelematic.treehub.db.ManifestRepositorySupport
import com.advancedtelematic.treehub.http.ManifestParser.ManifestInfo
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.{Decoder, DecodingFailure}
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext
import scala.util.Success



class ManifestResource(namespace: Directive1[Namespace],
                       messageBus: MessageBusPublisher)
                      (implicit db: Database, ec: ExecutionContext, mat: Materializer, scheduler: Scheduler) extends ManifestRepositorySupport {

  import akka.http.scaladsl.server.Directives._

  private val CommitPath = Segment.flatMap(s =>  eu.timepit.refined.refineV[ValidCommit](s).toOption)

  val route = namespace { ns =>
    path("manifests" / CommitPath) { commit =>
      put {
        entity(as[JsonDecodedEntity[ManifestInfo]]) { manifestEntity =>
          val objId = ObjectId.from(commit)
          val f = manifestRepository
            .persist(ns, objId, manifestEntity.json)
            .map(_ => StatusCodes.OK)
            .andThen {
              case Success(_) =>
                messageBus.publishSafe(CommitManifestUpdated(ns, commit, manifestEntity.entity.releaseBranch,
                                       manifestEntity.entity.metaUpdaterVersion, Instant.now))
            }

          complete(f)
        }
      }
    }
  }
}
