package com.advancedtelematic.treehub.http

import akka.http.scaladsl.server.Directive1
import com.advancedtelematic.data.DataType
import com.advancedtelematic.data.DataType.Commit
import com.advancedtelematic.treehub.db.RefRepositorySupport
import org.genivi.sota.data.Namespace
import slick.driver.MySQLDriver.api._
import org.genivi.sota.rest.Validation._

import scala.concurrent.ExecutionContext

class AtsResource(namespace: Directive1[Namespace])
                 (implicit db: Database, ec: ExecutionContext) extends RefRepositorySupport {
  import akka.http.scaladsl.server.Directives._

  private val extractCommit: Directive1[Commit] = refined[DataType.ValidCommit](Slash ~ Segment)

  val route = namespace { ns =>
    pathPrefix("ats") {
      (get & extractCommit & pathEnd) { commit =>
        complete(refRepository.getVersionForCommit(ns, commit))
      }
    }
  }
}