package com.advancedtelematic.treehub.http

import java.nio.file.Paths

import akka.http.scaladsl.model.StatusCodes

import com.advancedtelematic.libats.test.{DatabaseSpec, LongTest}
import com.advancedtelematic.util.{ResourceSpec, TreeHubSpec}
import cats.syntax.either._
import com.advancedtelematic.treehub.db.ManifestRepositorySupport

import com.advancedtelematic.libats.data.RefinedUtils._
import com.advancedtelematic.libats.messaging_datatype.DataType.ValidCommit
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

class ManifestResourceSpec extends TreeHubSpec with ResourceSpec with DatabaseSpec with LongTest with ManifestRepositorySupport {

  lazy val sampleManifestJson = Paths.get(this.getClass.getResource(s"/manifest.json").toURI)

  test("saves the manifest raw json to database") {
    val json = io.circe.jawn.parseFile(sampleManifestJson.toFile).valueOr(throw _)

    val commit = "9b6fb4217871440cdb9694cf86e8cf8b7d98c97fe20567b5f0ef4671a76aa051".refineTry[ValidCommit].get

    Put(apiUri(s"manifests/$commit"), json)  ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }

    val saved = manifestRepository.find(defaultNs, commit).futureValue
    saved shouldBe json
  }

  test("updates the saved manifest in db") (pending)

  test("publishes release branch to bus") (pending)

  test("publishes meta_updater version to bus") (pending)
}
