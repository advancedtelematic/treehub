package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.util.{ResourceSpec, TreeHubSpec}

class ConfigResourceSpec extends TreeHubSpec with ResourceSpec {

  test("GET config retrieves a config") {
    val expectedConfig =
      """
        |[core]
        |repo_version=1
        |mode=archive-z2
        |
      """.stripMargin

    Get(apiUri(s"config")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Array[Byte]] shouldBe expectedConfig.getBytes
    }
  }

}
