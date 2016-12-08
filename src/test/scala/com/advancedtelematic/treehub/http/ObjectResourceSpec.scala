/**
  * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
  * License: MPL-2.0
  */
package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials, RawHeader}
import com.advancedtelematic.common.DigestCalculator
import com.advancedtelematic.data.DataType.{Commit, ObjectId}
import com.advancedtelematic.treehub.db.ObjectRepositorySupport
import com.advancedtelematic.util.ResourceSpec.ClientTObject
import com.advancedtelematic.util.{ResourceSpec, TreeHubSpec}
import eu.timepit.refined.api.Refined
import org.genivi.sota.data.Namespace

import scala.util.Random

class ObjectResourceSpec extends TreeHubSpec with ResourceSpec with ObjectRepositorySupport {

  test("POST creates a new blob") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.objectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[String] shouldBe obj.checkSum
    }

    Get(apiUri(s"objects/${obj.objectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.OK

      responseAs[Array[Byte]] shouldBe obj.blob
    }
  }

  test("409 for already existing objects") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.objectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Post(apiUri(s"objects/${obj.objectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.Conflict
    }
  }

  test("404 for non existing objects") {
    val obj = new ClientTObject()

    Get(apiUri(s"objects/${obj.objectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("matches only valid commits") {
    Get(apiUri(s"objects/wat/w00t")) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("HEAD returns 404 if commit does not exist") {
    val obj = new ClientTObject()

    Head(apiUri(s"objects/${obj.objectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("HEAD returns 200 if commit exists") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.objectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[String] shouldBe obj.checkSum
    }

    Head(apiUri(s"objects/${obj.objectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }
}
