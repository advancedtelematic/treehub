package com.advancedtelematic.treehub.repo_metrics

import java.time.Instant
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Status}
import com.advancedtelematic.data.DataType.ObjectId
import com.advancedtelematic.treehub.object_store.ObjectStore
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter.{Done, UpdateBandwidth, UpdateStorage}
import org.genivi.sota.data.{Namespace, UpdateType}
import org.genivi.sota.messaging.MessageBusPublisher
import org.genivi.sota.messaging.Messages.{BandwidthUsage, ImageStorageUsage}

import scala.concurrent.Future


object UsageMetricsRouter {
  type HandlerRef = ActorRef

  case class UpdateStorage(namespace: Namespace)
  case class UpdateBandwidth(namespace: Namespace, usedBandwidthBytes: Long, objectId: ObjectId)
  case class Done(namespace: Namespace)

  def apply(messageBusPublisher: MessageBusPublisher, objectStore: ObjectStore): Props =
    Props(new UsageMetricsRouter(messageBusPublisher, objectStore))
}

class UsageMetricsRouter(messageBusPublisher: MessageBusPublisher, objectStore: ObjectStore) extends Actor {

  val storageActor = context.actorOf(StorageUpdate(messageBusPublisher, objectStore))
  val bandwidthActor = context.actorOf(BandwidthUpdate(messageBusPublisher, objectStore))

  override def receive: Receive = {
    case m: UpdateStorage =>
      storageActor.forward(m)
    case m: UpdateBandwidth =>
      bandwidthActor.forward(m)
  }
}

protected object StorageUpdate {
  def apply(messageBusPublisher: MessageBusPublisher, objectStore: ObjectStore): Props = {
    Props(new StorageUpdate(messageBusPublisher, objectStore))
  }
}

protected class StorageUpdate(publisher: MessageBusPublisher, objectStore: ObjectStore) extends Actor with ActorLogging {

  import akka.pattern.pipe
  import context.dispatcher

  private def update(namespace: Namespace): Future[Done] = {
    for {
      usage <- objectStore.usage(namespace)
      _ <- publisher.publish(ImageStorageUsage(namespace, Instant.now, usage))
    } yield Done(namespace)
  }

  override def receive: Receive = {
    case UpdateStorage(ns) =>
      update(ns).pipeTo(self)
    case Done(ns) =>
      log.info(s"published storage message for $ns")
    case Status.Failure(ex) =>
      log.error(ex, "Could not publish storage message")
  }
}


protected object BandwidthUpdate {
  def apply(messageBusPublisher: MessageBusPublisher, objectStore: ObjectStore): Props = {
    Props(new BandwidthUpdate(messageBusPublisher, objectStore))
  }
}


protected class BandwidthUpdate(publisher: MessageBusPublisher, objectStore: ObjectStore) extends Actor with ActorLogging {
  import context.dispatcher
  import akka.pattern.pipe

  private def update(namespace: Namespace, usedBandwidthBytes: Long, objectId: ObjectId): Future[Done] = {
    val uuid = UUID.nameUUIDFromBytes(Instant.now.toString.getBytes ++ objectId.get.getBytes)
    publisher.publish(BandwidthUsage(uuid, namespace, Instant.now, usedBandwidthBytes,
      UpdateType.Image, objectId.get)).map(_ => Done(namespace))
  }

  override def receive: Receive = {
    case UpdateBandwidth(ns, usedBytes, objectId) =>
      update(ns, usedBytes, objectId).pipeTo(self)
    case Done(ns) =>
      log.info(s"published bandwidth usage for $ns")
    case Status.Failure(ex) =>
      log.error(ex, "Could not publish bandwidth usage message")
  }
}
