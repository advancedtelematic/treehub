package com.advancedtelematic.treehub.http

import java.io.File
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.scaladsl.FileIO
import com.advancedtelematic.data.DataType.{Commit, ObjectId, ObjectStatus, Ref, RefName, TObject}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.Commit
import com.advancedtelematic.treehub.db.{ObjectRepositorySupport, RefRepositorySupport}
import com.advancedtelematic.util.{ResourceSpec, TreeHubSpec}
import org.scalatest.Suite
import org.scalatest.time.{Millis, Seconds, Span}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Future

class RefResourceIntegrationSpec extends TreeHubSpec with RefResourceScope with ObjectRepositorySupport with RefRepositorySupport {

  implicit val patience = PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  test("accepts commit if object points to previous refName") {
    val newCommit = for {
      (commit, obj) <- createCommitObject("initial_commit.blob", defaultNs)
      _ <- refRepository.persist(Ref(defaultNs, RefName("master"), commit, obj.id))
      (newCommit, newObj) <- createCommitObject("second_commit.blob", defaultNs)
    } yield newCommit


    Post(apiUri("refs/master"), newCommit.futureValue.value) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("accepts a commit without a parent") {
    val (commit, _) = createCommitObject("initial_commit.blob", defaultNs).futureValue

    Post(apiUri("refs/initial-master"), commit.value) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("rejects commit if object does not point to previous refName") {
    val commit = for {
      (commit, obj) <- createCommitObject("third_commit.blob", defaultNs)
      _ <- refRepository.persist(Ref(defaultNs, RefName("not-master"), commit, obj.id))
      (firstCommit, firstObj) <- createCommitObject("initial_commit.blob", defaultNs)
    } yield firstCommit

    Post(apiUri("refs/not-master"), commit.futureValue.value) ~> routes ~> check {
      status shouldBe StatusCodes.PreconditionFailed
    }
  }

  test("accepts invalid parent on force push") {
    val commit = for {
      (commit, obj) <- createCommitObject("third_commit.blob", defaultNs)
      _ <- refRepository.persist(Ref(defaultNs, RefName("master-force"), commit, obj.id))
      (firstCommit, firstObj) <- createCommitObject("initial_commit.blob", defaultNs)
    } yield firstCommit

    Post(apiUri("refs/master-force"), commit.futureValue.value)
      .addHeader(RawHeader("x-ats-ostree-force", "true")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }
}


trait RefResourceScope extends ResourceSpec {
  self: Suite =>

  val log: Logger = LoggerFactory.getLogger(getClass)

  def createCommitObject(fileName: String, ns: Namespace): Future[(Commit, TObject)] = {
    val file = new File(this.getClass.getResource(s"/blobs/$fileName").getFile)
    val blob = FileIO.fromPath(file.toPath)
    val blobBytes = blob.runReduce(_ ++ _).map(_.toArray)

    for {
      commit <- blobBytes.map(Commit.from).map(_.toOption.get)
      id = ObjectId.from(commit)
      tobj <- objectStore.storeFile(ns, id, file)
        .recover {
          case Errors.ObjectExists(_) =>
            log.info("TOBject already exists")
            TObject(ns, id, 0L, ObjectStatus.UPLOADED)
        }
    } yield (commit, tobj)
  }
}
