package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Location, RawHeader}
import com.advancedtelematic.data.DataType.ValidDeltaId
import com.advancedtelematic.treehub.db.ObjectRepositorySupport
import com.advancedtelematic.util.{ResourceSpec, TreeHubSpec}
import com.advancedtelematic.libats.data.RefinedUtils.RefineTry
import com.advancedtelematic.util.FakeUsageUpdate.CurrentBandwith
import akka.pattern.ask
import com.advancedtelematic.data.DataType.CommitTupleOps

import scala.concurrent.duration._
import cats.syntax.either._
import com.advancedtelematic.libats.messaging_datatype.DataType.Commit
import eu.timepit.refined.api.Refined

class DeltaResourceSpec extends TreeHubSpec with ResourceSpec with ObjectRepositorySupport {
  test("GET summary retrieves a summary") {
    deltaStore.storeSummary(defaultNs, "some data".getBytes)

    Get(apiUri(s"summary")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Array[Byte]] shouldBe "some data".getBytes
    }
  }

  test("GET summary returns 404 Not Found if summary is not in the delta store") {
    Get(apiUri(s"summary")) ~> RawHeader("x-ats-namespace", "notfound") ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("GET on delta superblock returns superblock") {
    val deltaId = "81/18918981-278278".refineTry[ValidDeltaId].get
    deltaStore.storeBytes(defaultNs, deltaId, "superblock", "some superblock data".getBytes)

    Get(apiUri(s"deltas/${deltaId.value}/superblock")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Array[Byte]] shouldBe "some superblock data".getBytes
    }
  }

  test("GET on deltas using from/to redirects to url with deltaId") {
    val from: Commit = Refined.unsafeApply("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824")
    val to: Commit = Refined.unsafeApply("82e35a63ceba37e9646434c5dd412ea577147f1e4a41ccde1614253187e3dbf9")
    val deltaId = (from, to).toDeltaId

    Get(apiUri(s"deltas?from=${from.value}&to=${to.value}")) ~> routes ~> check {
      status shouldBe StatusCodes.Found
      header[Location].map(_.uri.toString()) should contain(s"/deltas/${deltaId.value}")
    }
  }

  test("publishes usage to bus") {
    val deltaId = "6q/dddbmoaLtYLmZg2Q8OZm7syoxvAOFs0fAXbavojKY-k_F8QpdRP9KlPD6wiS2HNF7WuL1sgfu1tLaJXV6GjIU".refineTry[ValidDeltaId].get
    val blob = "some other data".getBytes
    deltaStore.storeBytes(defaultNs, deltaId, "superblock", blob)

    Get(apiUri(s"deltas/${deltaId.value}/superblock")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val usage = fakeUsageUpdate.ask(CurrentBandwith(deltaId.asObjectId.valueOr(throw _)))(1.second).mapTo[Long].futureValue
    usage should be >= blob.length.toLong
  }
}
