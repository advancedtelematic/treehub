package com.advancedtelematic.treehub.http

class ConfResource {

  import akka.http.scaladsl.server.Directives._

  val route = path("config") {
    val c =
      """
        |[core]
        |repo_version=1
        |mode=archive-z2
        |
      """.stripMargin

    complete(c)
  }
}
