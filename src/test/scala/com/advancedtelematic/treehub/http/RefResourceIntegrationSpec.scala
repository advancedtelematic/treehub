package com.advancedtelematic.treehub.http

import java.io.File
import java.nio.file.Files

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import com.advancedtelematic.data.DataType.{Commit, ObjectId, Ref, RefName, TObject}
import com.advancedtelematic.treehub.db.ObjectRepository._
import com.advancedtelematic.treehub.db.{ObjectRepositorySupport, RefRepositorySupport}
import com.advancedtelematic.util
import com.advancedtelematic.util.ResourceSpec
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

import scala.concurrent.Future

class RefResourceIntegrationSpec extends util.TreeHubSpec with ResourceSpec with ObjectRepositorySupport with RefRepositorySupport {

  val log = LoggerFactory.getLogger(this.getClass)

  def createCommitObject(fileName: String): Future[(Commit, TObject)] = {
    val blob = Files.readAllBytes(new File(this.getClass.getResource(s"/blobs/$fileName").getFile).toPath)
    val commit = Commit.from(blob).toOption.get
    val id = ObjectId.from(commit)

    val obj = TObject(defaultNs, id, blob)

    val f = for {
      obj <- objectRepository.create(obj)
    } yield (commit, obj)

    f.recover {
      case ObjectAlreadyExists =>
        log.info("TOBject already exists")
        (commit, obj)
    }
  }

  test("accepts commit if object points to previous ref") {
    val newCommit = for {
      (commit, obj) <- createCommitObject("initial_commit.blob")
      _ <- refRepository.persist(Ref(defaultNs, RefName("master"), commit, obj.id))
      (newCommit, newObj) <- createCommitObject("second_commit.blob")
    } yield newCommit


    Post(apiUri("refs/master"), newCommit.futureValue.get) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("accepts a commit without a parent") {
    val (commit, _) = createCommitObject("initial_commit.blob").futureValue

    Post(apiUri("refs/initial-master"), commit.get) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("rejects commit if object does not point to previous ref") {
    val commit = for {
      (commit, obj) <- createCommitObject("third_commit.blob")
      _ <- refRepository.persist(Ref(defaultNs, RefName("not-master"), commit, obj.id))
      (firstCommit, firstObj) <- createCommitObject("initial_commit.blob")
    } yield firstCommit

    Post(apiUri("refs/not-master"), commit.futureValue.get) ~> routes ~> check {
      status shouldBe StatusCodes.PreconditionFailed
    }
  }

  test("accepts invalid parent on force push") {
    val commit = for {
      (commit, obj) <- createCommitObject("third_commit.blob")
      _ <- refRepository.persist(Ref(defaultNs, RefName("master-force"), commit, obj.id))
      (firstCommit, firstObj) <- createCommitObject("initial_commit.blob")
    } yield firstCommit

    Post(apiUri("refs/master-force"), commit.futureValue.get)
      .addHeader(RawHeader("x-ats-ostree-force", "true")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }
}
