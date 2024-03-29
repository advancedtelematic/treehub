package com.advancedtelematic.util

import akka.actor.{Actor, ActorLogging, Props, Scheduler}
import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.model.{HttpEntity, MediaTypes, Multipart}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.advancedtelematic.common.DigestCalculator
import com.advancedtelematic.data.DataType.{Commit, ObjectId}
import com.advancedtelematic.treehub.Settings
import com.advancedtelematic.treehub.client._
import com.advancedtelematic.treehub.http._
import com.advancedtelematic.treehub.object_store.{LocalFsBlobStore, ObjectStore}
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter.{UpdateBandwidth, UpdateStorage}
import com.advancedtelematic.util.FakeUsageUpdate.{CurrentBandwith, CurrentStorage}
import eu.timepit.refined.api.Refined

import java.nio.file.Files
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.Suite

import scala.util.Random
import cats.syntax.either._
import com.advancedtelematic.libats.auth.NamespaceDirectives
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging.test.MockMessageBus
import com.advancedtelematic.libats.messaging_datatype.DataType.Commit
import com.advancedtelematic.libats.test.DatabaseSpec
import com.advancedtelematic.treehub.delta_store.LocalDeltaStorage

object ResourceSpec {
  class ClientTObject(val blobStr: String = Random.nextString(10)) {
    lazy val blob = blobStr.getBytes

    lazy val checkSum = DigestCalculator.digest()(blobStr)

    private lazy val (prefix, rest) = checkSum.splitAt(2)

    lazy val prefixedObjectId = s"$prefix/$rest.commit"

    lazy val objectId = ObjectId.parse(s"$prefix$rest.commit").toOption.get

    lazy val commit: Commit = Refined.unsafeApply(checkSum)

    lazy val formFile =
      BodyPart("file", HttpEntity(MediaTypes.`application/octet-stream`, blob),
        Map("filename" -> s"$checkSum.commit"))

    lazy val form = Multipart.FormData(formFile)

    lazy val byteSource = Source.single(ByteString(blob))
  }

}

object FakeUsageUpdate {
  case class CurrentStorage(ns: Namespace)
  case class CurrentBandwith(objectId: ObjectId)
}

class FakeUsageUpdate extends Actor with ActorLogging {
  var storageUsages = Map.empty[Namespace, Long]
  var bandwidthUsages = Map.empty[ObjectId, Long]


  override def receive: Receive = {
    case UpdateStorage(ns) =>
      storageUsages += (ns -> (storageUsages.getOrElse(ns, 0l) + 1l))
      log.info(s"Would publish storage bus message for $ns")

    case u @ UpdateBandwidth(_, usedBandwidth, objectId) =>
      bandwidthUsages += (objectId -> (bandwidthUsages.getOrElse(objectId, 0l) + usedBandwidth))
      log.info(s"Would publish bw bus message for $u")

    case CurrentStorage(ns) =>
      sender ! storageUsages.getOrElse(ns, 0l)

    case CurrentBandwith(objectId) =>
      sender ! bandwidthUsages.getOrElse(objectId, 0l)
  }
}

trait ResourceSpec extends ScalatestRouteTest with DatabaseSpec with Settings {
  self: Suite =>

  def apiUri(path: String): String = "/api/v2/" + path
  def apiUri(version: Int, path: String): String = s"/api/v$version/" + path

  lazy val namespaceExtractor = NamespaceDirectives.defaultNamespaceExtractor.map(_.namespace)

  implicit val scheduler: Scheduler = system.scheduler

  val objectStore = new ObjectStore(new LocalFsBlobStore(Files.createTempDirectory("treehub-obj")))

  val deltaStore = new LocalDeltaStorage(Files.createTempDirectory("treehub-deltas"))

  val fakeUsageUpdate = system.actorOf(Props(new FakeUsageUpdate), "fake-usage-update")

  lazy val messageBus = new MockMessageBus()

  lazy val routes = new TreeHubRoutes(
    namespaceExtractor,
    namespaceExtractor,
    messageBus,
    objectStore,
    deltaStore,
    fakeUsageUpdate).routes
}
