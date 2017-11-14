package com.advancedtelematic.treehub.repo_metrics

import akka.testkit.TestActorRef
import com.advancedtelematic.data.DataType.{ObjectId, TObject}
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter.UpdateStorage
import com.advancedtelematic.util.TreeHubSpec
import cats.syntax.either._
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.Messages.ImageStorageUsage

import scala.concurrent.duration._

class StorageUpdateSpec extends TreeHubSpec with UsageUpdateSpec {
  lazy val subject = TestActorRef(new StorageUpdate(messageBus, objectStore, 2.second))

  import system.dispatcher

  system.eventStream.subscribe(testActor, classOf[ImageStorageUsage])

  val text = "some text, more text"
  val objId =  ObjectId.parse("bc27b27e4dff813880183a339d903d2f45529ee81d543c755e8ccdae5a907311.commit").toOption.get

  test("sends update message to bus") {
    objectRepository.create(TObject(defaultNs, objId, text.length))

    subject ! UpdateStorage(defaultNs)

    expectMsgPF(3.seconds, "message with len == text.length") {
      case p @ ImageStorageUsage(ns, _, len) if (ns == defaultNs) && (len == text.length) => p
    }
  }

  test("sends an update message for each namespace") {
    val ns0 = Namespace("namespace0")
    val ns1 = Namespace("namespace1")

    objectRepository.create(TObject(ns0, objId, text.length))
    objectRepository.create(TObject(ns1, objId, text.length))

    subject ! UpdateStorage(ns0)
    subject ! UpdateStorage(ns1)

    expectMsgPF(3.seconds, s"message with ns in ($ns0, $ns1)") {
      case p @ ImageStorageUsage(ns, _, _) if ns == ns0 || ns == ns1 => p
    }

    expectMsgPF(3.seconds, s"message with ns in ($ns0, $ns1)") {
      case p @ ImageStorageUsage(ns, _, _) if ns == ns0 || ns == ns1 => p
    }
  }
}
