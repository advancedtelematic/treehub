package com.advancedtelematic.treehub.daemon

import com.advancedtelematic.data.DataType.{ObjectId, ObjectStatus, Ref, RefName}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.http.Errors.{MissingEntity, MissingEntityId}
import com.advancedtelematic.libats.messaging_datatype.Messages.OSTreeTargetDelete
import com.advancedtelematic.libats.test.DatabaseSpec
import com.advancedtelematic.treehub.db.{ManifestRepositorySupport, ObjectRepositorySupport, RefRepositorySupport}
import com.advancedtelematic.treehub.http.RefResourceScope
import com.advancedtelematic.util.{DefaultPatience, TreeHubSpec}
import io.circe.Json
import org.scalatest.concurrent.PatienceConfiguration

class OSTreeTargetDeleteListenerSpec extends TreeHubSpec
  with RefResourceScope
  with DatabaseSpec
  with ObjectRepositorySupport
  with PatienceConfiguration
  with RefRepositorySupport
  with ManifestRepositorySupport
  with DefaultPatience {

  test("removes object from store") {
    val ns = Namespace.generate
    val refName = RefName("heads/xLfqd6MLYG")
    val manifest = Json.obj()

    val obj = (for {
      (commit, obj) <- createCommitObject("initial_commit.blob", ns)
      _ <- refRepository.persist(Ref(ns, refName, commit, obj.id))
      _ <- manifestRepository.persist(ns, obj.id, manifest)
    } yield obj).futureValue

    objectRepository.find(ns, obj.id).futureValue.status shouldBe ObjectStatus.UPLOADED
    refRepository.find(ns, refName).futureValue.objectId shouldBe obj.id
    manifestRepository.find(ns, obj.id).futureValue.contents shouldBe manifest

    OSTreeTargetDeleteListener.delete(objectStore)(OSTreeTargetDelete(ns)).futureValue

    objectRepository.exists(obj.namespace, obj.id).futureValue shouldBe false
    refRepository.find(ns, refName).failed.futureValue shouldBe a[MissingEntity[Ref]]
    manifestRepository.find(ns, obj.id).failed.futureValue shouldBe a[MissingEntityId[(Namespace, ObjectId)]]
  }
}
