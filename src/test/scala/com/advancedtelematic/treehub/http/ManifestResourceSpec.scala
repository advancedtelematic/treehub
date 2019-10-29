package com.advancedtelematic.treehub.http

import java.nio.file.Paths

import akka.http.scaladsl.model.StatusCodes
import cats.syntax.either._
import com.advancedtelematic.libats.messaging_datatype.Messages.CommitManifestUpdated
import com.advancedtelematic.libats.test.{DatabaseSpec, LongTest}
import com.advancedtelematic.treehub.db.ManifestRepositorySupport
import com.advancedtelematic.util.ResourceSpec.ClientTObject
import com.advancedtelematic.util.{ResourceSpec, TreeHubSpec}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Json
import io.circe.syntax._

class ManifestResourceSpec extends TreeHubSpec with ResourceSpec with DatabaseSpec with LongTest with ManifestRepositorySupport {
  lazy val sampleManifestJsonFile = Paths.get(this.getClass.getResource(s"/manifest.json").toURI).toFile
  lazy val sampleManifestJson = io.circe.jawn.parseFile(sampleManifestJsonFile).valueOr(throw _)

  test("saves the manifest raw json to database") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Put(apiUri(s"manifests/${obj.commit}"), sampleManifestJson)  ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val saved = manifestRepository.find(defaultNs, obj.objectId).futureValue
    saved.contents shouldBe sampleManifestJson
  }

  test("updates the saved manifest in db") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Put(apiUri(s"manifests/${obj.commit}"), sampleManifestJson)  ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val otherManifest = Json.obj(
      "manifest" -> Json.obj(
        "default" ->
          Map("@revision" -> "thud").asJson,
        "project" -> Json.arr(
        Map(
          "@path" -> "meta-updater",
          "@revision" -> "other revision",
        ).asJson
        )
      )
    )

    Put(apiUri(s"manifests/${obj.commit}"), otherManifest)  ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val saved = manifestRepository.find(defaultNs, obj.objectId).futureValue
    saved.contents shouldBe otherManifest
  }

  test("publishes manifest data to bus") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Put(apiUri(s"manifests/${obj.commit}"), sampleManifestJson)  ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val msg = messageBus.findReceived { msg: CommitManifestUpdated =>
      msg.commit == obj.commit
    }

    msg.map(_.releaseBranch) should contain("master")
    msg.map(_.metaUpdaterVersion) should contain("6e1c9cf5cc59437ce07f5aec2dc62d665d218bdb")
  }
}
