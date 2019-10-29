package com.advancedtelematic.treehub.http

import akka.http.scaladsl.server.Directive1
import akka.stream.Materializer
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.ValidCommit
import com.advancedtelematic.treehub.db.ManifestRepositorySupport
import com.advancedtelematic.treehub.http.ManifestParser.ManifestInfo
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.{Decoder, DecodingFailure, Json}
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext

case class JsonDecodedEntity[T : Decoder](json: Json, entity: T)

object JsonDecodedEntity {
  implicit def jsonEntityDecoder[T](implicit decoder: Decoder[T]): Decoder[JsonDecodedEntity[T]] =
    Decoder.decodeJson.emapTry { json =>
      json.as[T].map { obj =>
        JsonDecodedEntity(json, obj)
      }.toTry
    }
}

object ManifestParser {
  case class ManifestInfo(releaseBranch: String, metaUpdaterVersion: String)

  implicit val manifestDecoder: Decoder[ManifestInfo] = Decoder.instance { acursor =>
    val cursor = acursor.downField("manifest")

    val releaseBranchVersionsEither = for {
      releaseBranch <- cursor.downField("default").downField("@revision").as[String]
      metaUpdaterVersion <- cursor.downField("project").as[List[Map[String, String]]]
    } yield releaseBranch -> metaUpdaterVersion

    releaseBranchVersionsEither.flatMap { case (releaseBranch, versions) =>
      val metaUpdaterVersionObj = versions.flatMap { obj =>
        if(obj.get("@path").contains("meta-updater"))
          List(obj.get("@revision"))
        else
          List.empty
      }.flatten.headOption

      metaUpdaterVersionObj match {
        case Some(o) =>
          Right(ManifestInfo(releaseBranch, o))
        case None =>
          Left(DecodingFailure("Could not decode meta updater version from manifest",
            cursor.downField("project").history))
      }
    }
  }
}

class ManifestResource(namespace: Directive1[Namespace])
                      (implicit db: Database, ec: ExecutionContext, mat: Materializer) extends ManifestRepositorySupport {

  import akka.http.scaladsl.server.Directives._

  private val CommitPath = Segment.flatMap(s =>  eu.timepit.refined.refineV[ValidCommit](s).toOption)

  val route = namespace { ns =>
    path("manifests" / CommitPath) { commit =>
      put {
        entity(as[JsonDecodedEntity[ManifestInfo]]) { manifestEntity =>
          val f = manifestRepository.persist(ns, commit, manifestEntity.json)

          complete(f)
          // TODO: Send msg
        }
      }
    }
  }
}
