package com.advancedtelematic.treehub.repo_metrics

import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.{TestActorRef, TestKitBase}
import cats.data.Xor
import com.advancedtelematic.data.DataType.{ObjectId, TObject}
import com.advancedtelematic.treehub.db.ObjectRepositorySupport
import com.advancedtelematic.treehub.object_store.{LocalFsBlobStore, ObjectStore}
import com.advancedtelematic.treehub.repo_metrics.StorageUpdate.Update
import com.advancedtelematic.util.{DatabaseSpec, TreeHubSpec}
import org.genivi.sota.messaging.MessageBus
import org.genivi.sota.messaging.Messages.ImageStorageUsage

import scala.concurrent.duration._

class StorageUpdateSpec extends TreeHubSpec with DatabaseSpec with TestKitBase  with ObjectRepositorySupport {
  override implicit lazy val system: ActorSystem = ActorSystem("StorageUpdateSpec")

  import system.dispatcher

  implicit val mat = ActorMaterializer()

  lazy val localFsDir = Files.createTempDirectory("StorageUpdateSpec")

  lazy val namespaceDir = Files.createDirectories(Paths.get(s"${localFsDir.toAbsolutePath}/${defaultNs.get}"))

  lazy val objectStore = new ObjectStore(new LocalFsBlobStore(localFsDir.toFile))

  lazy val messageBus = MessageBus.publisher(system, config) match {
    case Xor.Right(mbp) => mbp
    case Xor.Left(err) => throw err
  }

  lazy val subject = TestActorRef(new StorageUpdate(messageBus, objectStore))

  system.eventStream.subscribe(testActor, classOf[ImageStorageUsage])

  test("sends update message to bus") {
    val text = "some text, more text"
    val objId =  ObjectId.parse("bc27b27e4dff813880183a339d903d2f45529ee81d543c755e8ccdae5a907311.commit").toOption.get
    objectRepository.create(TObject(defaultNs, objId, text.length))

    subject ! Update(defaultNs)

    expectMsgPF(10.seconds, "message with len == text.length") {
      case p @ ImageStorageUsage(ns, _, len) if (ns == defaultNs) && (len == text.length) => p
    }
  }
}
