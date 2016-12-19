/**
  * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
  * License: MPL-2.0
  */
package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model._
import akka.pattern.ask
import com.advancedtelematic.treehub.db.ObjectRepositorySupport
import com.advancedtelematic.util.FakeStorageUpdate.CurrentUsage
import com.advancedtelematic.util.ResourceSpec.ClientTObject
import com.advancedtelematic.util.{ResourceSpec, TreeHubSpec}

import scala.concurrent.duration._

class ObjectResourceSpec extends TreeHubSpec with ResourceSpec with ObjectRepositorySupport {

  test("POST creates a new blob") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.objectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"objects/${obj.objectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.OK

      responseAs[Array[Byte]] shouldBe obj.blob
    }
  }

  test("POST hints updater to update current storage") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.objectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val usage = fakeStorageUpdate.ask(CurrentUsage(defaultNs))(1.second).mapTo[Long].futureValue

    usage should be >= 1l
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
    }

    Head(apiUri(s"objects/${obj.objectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }
}
