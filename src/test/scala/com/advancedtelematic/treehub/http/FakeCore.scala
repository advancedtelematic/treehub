/**
  * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
  * License: MPL-2.0
  */
package com.advancedtelematic.treehub.http

import com.advancedtelematic.data.DataType.{Commit, Ref}
import com.advancedtelematic.treehub.client.Core
import org.genivi.sota.data.Namespace

import scala.concurrent.{ExecutionContext, Future}

class FakeCore() extends Core {
  override def publishRef(ref: Ref, description: String)
                         (implicit ec: ExecutionContext): Future[Unit] = Future.successful(())
}
