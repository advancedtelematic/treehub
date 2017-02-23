/**
  * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
  * License: MPL-2.0
  */
package com.advancedtelematic.treehub.client

import com.advancedtelematic.data.DataType.{Commit, Ref}
import org.genivi.sota.data.Namespace

import scala.concurrent.{ExecutionContext, Future}

class FakeHttpCore() extends Core {
  override def publishRef(ref: Ref, description: String)
                         (implicit ec: ExecutionContext): Future[Unit] = Future.successful(())
}
