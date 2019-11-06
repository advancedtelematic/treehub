package com.advancedtelematic.treehub.http

import io.circe.{Decoder, DecodingFailure}

object ManifestParser {
  case class ManifestInfo(releaseBranch: String, metaUpdaterVersion: String)

  implicit val manifestDecoder: Decoder[ManifestInfo] = Decoder.instance { acursor =>
    val cursor = acursor.downField("manifest")

    val releaseBranchVersionsEither = for {
      releaseBranch <- cursor.downField("default").downField("@revision").as[String]
      metaUpdaterVersions <- cursor.downField("project").as[List[Map[String, String]]]
    } yield releaseBranch -> metaUpdaterVersions

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
