package com.advancedtelematic.treehub.repo_metrics

import cats.syntax.either._
import akka.testkit.{TestActorRef, TestKitBase}
import com.advancedtelematic.data.DataType.ObjectId
import com.advancedtelematic.libats.messaging_datatype.DataType.UpdateType
import com.advancedtelematic.libats.messaging_datatype.Messages.BandwidthUsage
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter.UpdateBandwidth
import com.advancedtelematic.util.TreeHubSpec

import scala.concurrent.duration._

class BandwidthUpdateSpec extends TreeHubSpec with UsageUpdateSpec with TestKitBase {

  lazy val subject = TestActorRef(new BandwidthUpdate(messageBus, objectStore))

  system.eventStream.subscribe(testActor, classOf[BandwidthUsage])

  test("sends bandwidth update message to bus") {
    val text = "some text, more text"
    val objectId = ObjectId.parse("15c9eae2827348b3a256f339a4f2010e97eda896137a904bacdd505949362721.filez").toOption.get

    subject ! UpdateBandwidth(defaultNs, text.length, objectId)

    expectMsgPF(10.seconds, "message with len == text.length") {
      case p @ BandwidthUsage(_, ns, _, len, updateType, objId)
        if (updateType == UpdateType.Image) && (ns == defaultNs) && (len == text.length) && (objId == objectId.value) => p
    }
  }
}
