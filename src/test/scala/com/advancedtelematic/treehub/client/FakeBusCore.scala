/**
  * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
  * License: MPL-2.0
  */
package com.advancedtelematic.treehub.client

import akka.actor.ActorSystem
import com.advancedtelematic.data.DataType.{Commit, Ref}
import com.advancedtelematic.libats.messaging.LocalMessageBus

import scala.concurrent.{ExecutionContext, Future}

class FakeBusCore()
      (implicit val system: ActorSystem) extends Core {

  val fakeMsgPublisher = LocalMessageBus.publisher(system)

  override def publishRef(ref: Ref, description: String)
                         (implicit ec: ExecutionContext): Future[Unit] = Future.successful(())

}
