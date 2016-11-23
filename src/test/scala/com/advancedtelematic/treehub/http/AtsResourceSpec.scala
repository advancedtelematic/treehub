package com.advancedtelematic.treehub.http

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.util.ResourceSpec.ClientTObject
import com.advancedtelematic.util.{ResourceSpec, TreeHubSpec}
import eu.timepit.refined.api.{Refined, Validate}

class AtsResourceSpec extends TreeHubSpec with ResourceSpec {

  implicit def refinedMarshaller[P](implicit p: Validate.Plain[String, P]): ToEntityMarshaller[Refined[String, P]] =
    Marshaller.StringMarshaller.compose(_.get)

  test("GET a commit hash returns version") {
    val obj = new ClientTObject()
    val ref = obj.commit

    Post(apiUri(s"objects/${obj.objectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[String] shouldBe obj.checkSum
    }

    Post(apiUri("refs/commit/version"), ref) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"ats/${obj.commit.get}")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[String] shouldBe "0.0.0"
    }
  }

  test("GET a commit hash returns custom version") {
    val obj = new ClientTObject()
    val ref = obj.commit
    val version = "0.0.2"

    Post(apiUri(s"objects/${obj.objectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[String] shouldBe obj.checkSum
    }

    Post(apiUri(s"refs/commit/customVersion?version=$version"), ref) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"ats/${obj.commit.get}")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[String] shouldBe version
    }
  }
}
