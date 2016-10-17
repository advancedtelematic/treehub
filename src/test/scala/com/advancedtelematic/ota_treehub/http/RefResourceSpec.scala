package com.advancedtelematic.ota_treehub.http

import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.common.DigestCalculator
import com.advancedtelematic.util.{ResourceSpec, TreeHubSpec}

import scala.util.Random

class RefResourceSpec extends TreeHubSpec with ResourceSpec {
  test("POST creates a new ref, GET returns") {
    val ref = DigestCalculator.digest()(Random.nextString(10))

    Post(apiUri("refs/some/ref"), ref) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri("refs/some/ref")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[String] shouldBe ref
    }
  }

  test("404 when ref is not found") {
    Get(apiUri("refs/some/other/ref")) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }
}
