package com.advancedtelematic.util

import java.nio.file.Files

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.model.{HttpEntity, MediaTypes, Multipart}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.advancedtelematic.common.DigestCalculator
import com.advancedtelematic.data.DataType.{Commit, ObjectId}
import com.advancedtelematic.treehub.http._
import com.advancedtelematic.treehub.object_store.{LocalFsBlobStore, ObjectStore}
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter.{UpdateBandwidth, UpdateStorage}
import com.advancedtelematic.util.FakeUsageUpdate.{CurrentBandwith, CurrentStorage}
import eu.timepit.refined.api.Refined
import org.genivi.sota.data.Namespace
import org.genivi.sota.http.NamespaceDirectives
import org.scalatest.Suite

import scala.util.Random

object ResourceSpec {
  class ClientTObject(blobStr: String = Random.nextString(10)) {
    lazy val blob = blobStr.getBytes

    lazy val checkSum = DigestCalculator.digest()(blobStr)

    private lazy val (prefix, rest) = checkSum.splitAt(2)

    lazy val prefixedObjectId = s"$prefix/$rest.commit"

    lazy val objectId = ObjectId.parse(s"$prefix$rest.commit").toOption.get

    lazy val commit: Commit = Refined.unsafeApply(checkSum)

    lazy val formFile =
      BodyPart("file", HttpEntity(MediaTypes.`application/octet-stream`, blobStr.getBytes),
        Map("filename" -> s"$checkSum.commit"))

    lazy val form = Multipart.FormData(formFile)
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

trait ResourceSpec extends ScalatestRouteTest with DatabaseSpec {
  self: Suite =>

  def apiUri(path: String): String = "/api/v2/" + path

  val testCore = new FakeCore()

  lazy val namespaceExtractor = NamespaceDirectives.defaultNamespaceExtractor.map(_.namespace)
  val objectStore = new ObjectStore(new LocalFsBlobStore(Files.createTempDirectory("treehub").toFile))

  val fakeUsageUpdate = system.actorOf(Props(new FakeUsageUpdate), "fake-usage-update")

  lazy val routes = new TreeHubRoutes(Directives.pass,
    namespaceExtractor,
    testCore,
    namespaceExtractor,
    objectStore,
    fakeUsageUpdate).routes
}
