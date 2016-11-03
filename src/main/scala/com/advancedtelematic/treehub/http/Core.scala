/**
  * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
  * License: MPL-2.0
  */
package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model.Uri
import com.advancedtelematic.data.DataType.{Commit, RefName}
import org.genivi.sota.data.Namespace

import scala.concurrent.{ExecutionContext, Future}

trait Core {
  def storeCommitInCore(ns: Namespace, commit: Commit, ref: RefName, description: String)
                       (implicit ec: ExecutionContext): Future[Unit]
}
