package com.advancedtelematic.treehub.http

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.common.DigestCalculator
import com.advancedtelematic.treehub.db.RefRepositorySupport
import com.advancedtelematic.util.ResourceSpec.ClientTObject
import com.advancedtelematic.util.{ResourceSpec, TreeHubSpec}
import eu.timepit.refined.api.{Refined, Validate}
import org.slf4j.LoggerFactory

import scala.util.Random

class RefResourceSpec extends TreeHubSpec with ResourceSpec with RefRepositorySupport {

  val log = LoggerFactory.getLogger(this.getClass)

  implicit def refinedMarshaller[P](implicit p: Validate.Plain[String, P]): ToEntityMarshaller[Refined[String, P]] =
    Marshaller.StringMarshaller.compose(_.get)

  test("POST creates a new ref, GET returns") {
    val obj = new ClientTObject()
    val ref = obj.commit

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Post(apiUri("refs/some/ref"), ref) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri("refs/some/ref")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[String] shouldBe ref.get
    }
  }

  test("POST operation is idempotent") {
    val obj = new ClientTObject()
    val ref = obj.commit

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Post(apiUri("refs/repeat"), ref) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Post(apiUri("refs/repeat"), ref) ~> routes ~> check {
      status shouldBe StatusCodes.OK
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
