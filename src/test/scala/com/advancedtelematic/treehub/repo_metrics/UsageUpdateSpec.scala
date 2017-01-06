package com.advancedtelematic.treehub.repo_metrics

import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKitBase
import cats.data.Xor
import com.advancedtelematic.treehub.db.ObjectRepositorySupport
import com.advancedtelematic.treehub.object_store.{LocalFsBlobStore, ObjectStore}
import com.advancedtelematic.util.TreeHubSpec
import org.genivi.sota.core.DatabaseSpec
import org.genivi.sota.messaging.MessageBus

trait UsageUpdateSpec extends DatabaseSpec with ObjectRepositorySupport with TestKitBase {
  self: TreeHubSpec =>

  override implicit lazy val system: ActorSystem = ActorSystem(this.getClass.getSimpleName)

  import system.dispatcher

  implicit val mat = ActorMaterializer()

  lazy val localFsDir = Files.createTempDirectory(this.getClass.getSimpleName)

  lazy val namespaceDir = Files.createDirectories(Paths.get(s"${localFsDir.toAbsolutePath}/${defaultNs.get}"))

  lazy val objectStore = new ObjectStore(new LocalFsBlobStore(localFsDir.toFile))

  lazy val messageBus = MessageBus.publisher(system, config) match {
    case Xor.Right(mbp) => mbp
    case Xor.Left(err) => throw err
  }
}
