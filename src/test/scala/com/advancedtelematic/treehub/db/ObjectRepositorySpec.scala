package com.advancedtelematic.treehub.db

import com.advancedtelematic.data.DataType.{ObjectId, TObject}
import com.advancedtelematic.util.{DatabaseSpec, DefaultPatience, TreeHubSpec}

import scala.concurrent.ExecutionContext

class ObjectRepositorySpec extends TreeHubSpec
  with DatabaseSpec
  with ObjectRepositorySupport
  with DefaultPatience {

  implicit val ec = ExecutionContext.global

  test("createOrUpdate updates if object exists") {
    val tobj = TObject(defaultNs, ObjectId.parse("456844b237e6e2a210953bd69ce321ee226e192c8eb4aef1b6e8a75e92bcffe8.filez").toOption.get, 200L)
    val updated = tobj.copy(byteSize = 400L)

    val f = for {
      _ <- objectRepository.createOrUpdate(tobj)
      _ <- objectRepository.createOrUpdate(updated)
      found <- objectRepository.find(tobj.namespace, tobj.id)
    } yield found

    f.futureValue shouldBe updated
  }
}
