package com.advancedtelematic.ota_treehub.http

import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.common.DigestCalculator
import com.advancedtelematic.data.DataType.{ObjectId, TObject}
import com.advancedtelematic.ota_treehub.db.ObjectRepositorySupport
import com.advancedtelematic.util.{ResourceSpec, TreeHubSpec}

import scala.util.Random

class RefResourceSpec extends TreeHubSpec with ResourceSpec with ObjectRepositorySupport {
  test("POST creates a new ref, GET returns") {
    val ref = DigestCalculator.digest()(Random.nextString(10))

    db.run(objectRepository.create(TObject(ObjectId(ref + ".commit"), Array.empty)))
      .futureValue

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

  test("returns 412 if commit does not exist") {
    val ref = DigestCalculator.digest()(Random.nextString(10))

    Post(apiUri("refs/some/new/ref"), ref) ~> routes ~> check {
      status shouldBe StatusCodes.PreconditionFailed
    }
  }
}
