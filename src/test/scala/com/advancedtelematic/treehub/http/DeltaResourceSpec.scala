package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.data.DataType.ValidDeltaId
import com.advancedtelematic.treehub.db.ObjectRepositorySupport
import com.advancedtelematic.util.{ResourceSpec, TreeHubSpec}
import com.advancedtelematic.libats.data.RefinedUtils.RefineTry
import com.advancedtelematic.util.FakeUsageUpdate.CurrentBandwith
import akka.pattern.ask
import scala.concurrent.duration._

class DeltaResourceSpec extends TreeHubSpec with ResourceSpec with ObjectRepositorySupport {
  test("GET summary retrieves a summary") {
    deltaStore.storeSummary(defaultNs, "some data".getBytes)

    Get(apiUri(s"summary")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Array[Byte]] shouldBe "some data".getBytes
    }
  }

  test("GET on delta superblock returns superblock") {
    val deltaId = "81/18918981-278278".refineTry[ValidDeltaId].get
    deltaStore.storeDelta(defaultNs, deltaId, "some superblock data".getBytes)

    Get(apiUri(s"deltas/${deltaId.get}/superblock")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Array[Byte]] shouldBe "some superblock data".getBytes
    }
  }

  test("publishes usage to bus") {
    val deltaId = "6q/dddbmoaLtYLmZg2Q8OZm7syoxvAOFs0fAXbavojKY-k_F8QpdRP9KlPD6wiS2HNF7WuL1sgfu1tLaJXV6GjIU".refineTry[ValidDeltaId].get
    val blob = "some other data".getBytes
    deltaStore.storeDelta(defaultNs, deltaId, blob)

    Get(apiUri(s"deltas/${deltaId.get}/superblock")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val usage = fakeUsageUpdate.ask(CurrentBandwith(deltaId.asObjectId.right.get))(1.second).mapTo[Long].futureValue
    usage should be >= blob.length.toLong
  }
}
