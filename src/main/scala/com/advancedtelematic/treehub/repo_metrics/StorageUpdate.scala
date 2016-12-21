package com.advancedtelematic.treehub.repo_metrics

import java.time.Instant

import akka.actor.{Actor, ActorLogging, Props, Status}
import com.advancedtelematic.treehub.object_store.ObjectStore
import com.advancedtelematic.treehub.repo_metrics.StorageUpdate.{Done, Update}
import org.genivi.sota.data.Namespace
import org.genivi.sota.messaging.MessageBusPublisher
import org.genivi.sota.messaging.Messages.ImageStorageUsage

import scala.concurrent.Future

object StorageUpdate {
  case class Update(namespace: Namespace)
  case class Done(namespace: Namespace)

  def apply(messageBusPublisher: MessageBusPublisher, objectStore: ObjectStore): Props = {
    Props(new StorageUpdate(messageBusPublisher, objectStore))
  }
}

class StorageUpdate(publisher: MessageBusPublisher, objectStore: ObjectStore) extends Actor with ActorLogging {

  import akka.pattern.pipe
  import context.dispatcher

  private def update(namespace: Namespace): Future[Done] = {
    for {
      usage <- objectStore.usage(namespace)
      _ <- publisher.publish(ImageStorageUsage(namespace, Instant.now, usage))
    } yield Done(namespace)
  }

  override def receive: Receive = {
    case Update(ns) =>
      update(ns).pipeTo(self)
    case Done(ns) =>
      log.info(s"published storage message for $ns")
    case Status.Failure(ex) =>
      log.error(ex, "Could not publish storage message")
  }
}
