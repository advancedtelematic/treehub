/**
  * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
  * License: MPL-2.0
  */
package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.pattern.ask
import com.advancedtelematic.treehub.db.ObjectRepositorySupport
import com.advancedtelematic.util.FakeUsageUpdate.{CurrentBandwith, CurrentStorage}
import com.advancedtelematic.util.ResourceSpec.ClientTObject
import com.advancedtelematic.util.{ResourceSpec, TreeHubSpec}
import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers.byteArrayUnmarshaller
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.unmarshaller
import com.advancedtelematic.libats.data.ErrorRepresentation
import com.advancedtelematic.libats.data.ErrorRepresentation._

import scala.concurrent.duration._

class ObjectResourceSpec extends TreeHubSpec with ResourceSpec with ObjectRepositorySupport {

  test("POST creates a new blob when uploading form with `file` field") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Array[Byte]] shouldBe obj.blob
    }
  }

  // This is the same as using `curl -H "Content-Type: application/octet-stream" --data-binary @file`
  test("POST creates a new blob when uploading application/octet-stream directly") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.blob) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    Get(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Array[Byte]] shouldBe obj.blob
    }
  }

  test("cannot report object upload when object is not in status CLIENT_UPLOADING") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.blob) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    Put(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("cannot use redirects when storage method is local storage") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}?size=${obj.blob.length}"))
      .addHeader(RawHeader("x-ats-accept-redirect", "true")) ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.OutOfBandStorageNotSupported
    }
  }

  test("POST hints updater to update current storage") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val usage = fakeUsageUpdate.ask(CurrentStorage(defaultNs))(1.second).mapTo[Long].futureValue

    usage should be >= 1l
  }

  test("GET hints updater to update current bandwidth") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val usage = fakeUsageUpdate.ask(CurrentBandwith(obj.objectId))(1.second).mapTo[Long].futureValue

    usage should be >= obj.blob.length.toLong
  }

  test("409 for already existing objects") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.Conflict
    }
  }

  test("404 for non existing objects") {
    val obj = new ClientTObject()

    Get(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
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

    Head(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("HEAD returns 200 if commit exists") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Head(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }
}
