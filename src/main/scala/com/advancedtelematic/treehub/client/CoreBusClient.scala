/**
  * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
  * License: MPL-2.0
  */
package com.advancedtelematic.treehub.client

import scala.concurrent.ExecutionContext
import akka.http.scaladsl.model.Uri
import org.genivi.sota.messaging.MessageBusPublisher
import scala.concurrent.{ExecutionContext, Future}
import com.advancedtelematic.data.DataType.{Commit, Ref, RefName}
import org.genivi.sota.messaging.Messages.TreehubCommit


class CoreBusClient(messageBusPublisher: MessageBusPublisher, treeHubUri: Uri) extends Core {

  private def mkTreehubCommit(ref: Ref, description: String): TreehubCommit = {
    //TODO: PRO-1802 pass the refname as the description until we can parse the real description out of the commit
    val formattedRefName = ref.name.get.replaceFirst("^heads/", "").replace("/", "-")
    val size = 0 // TODO
    TreehubCommit(ref.namespace, ref.value.get, formattedRefName, description, size, treeHubUri.toString)
  }

  def publishRef(ref: Ref, description: String)
                (implicit ec: ExecutionContext): Future[Unit] =
    messageBusPublisher
      .publishSafe(mkTreehubCommit(ref, description))
      .map(_ => ())

}
