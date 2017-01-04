package com.advancedtelematic.treehub.repo_metrics

import akka.testkit.TestActorRef
import com.advancedtelematic.data.DataType.{ObjectId, TObject}
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter.UpdateStorage
import com.advancedtelematic.util.TreeHubSpec
import org.genivi.sota.messaging.Messages.ImageStorageUsage

import scala.concurrent.duration._

class StorageUpdateSpec extends TreeHubSpec with UsageUpdateSpec {
  lazy val subject = TestActorRef(new StorageUpdate(messageBus, objectStore))

  import system.dispatcher

  system.eventStream.subscribe(testActor, classOf[ImageStorageUsage])

  test("sends update message to bus") {
    val text = "some text, more text"
    val objId =  ObjectId.parse("bc27b27e4dff813880183a339d903d2f45529ee81d543c755e8ccdae5a907311.commit").toOption.get
    objectRepository.createOrUpdate(TObject(defaultNs, objId, text.length))

    subject ! UpdateStorage(defaultNs)

    expectMsgPF(10.seconds, "message with len == text.length") {
      case p @ ImageStorageUsage(ns, _, len) if (ns == defaultNs) && (len == text.length) => p
    }
  }
}
