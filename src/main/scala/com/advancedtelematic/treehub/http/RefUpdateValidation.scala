package com.advancedtelematic.treehub.http

import java.io.{File, IOException}
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}

import scala.collection.JavaConverters._
import com.advancedtelematic.data.DataType.{TObject, _}
import com.advancedtelematic.libostree.LibOstree
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object RefUpdateValidation {
  val _log = LoggerFactory.getLogger(this.getClass)

  private val ostreeRepositoryConfig =
    """
      |[core]
      |repo_version=1
      |mode=archive-z2
      |
    """.stripMargin.split('\n').toList.asJava

  private def setupFakeRepository(): Path = {
    val repoDir = Files.createTempDirectory("treehub-tmprepo")
    val config = Files.createFile(new File(repoDir.toString, "config").toPath)
    Files.createDirectory(new File(repoDir.toString, "tmp").toPath)
    Files.write(config, ostreeRepositoryConfig)
    repoDir
  }

  private def writeObjectToDisk(repoDir: Path, commitObject: TObject): Unit = {
    _log.info(s"Writing fake repo to ${repoDir.toString}")

    val (prefix, rest) = commitObject.id.get.splitAt(2)
    Files.createDirectories(new File(repoDir.toString, s"objects/$prefix").toPath)

    val p = new File(repoDir.toString, s"objects/$prefix/$rest")
    Files.write(p.toPath, commitObject.blob)
  }

  private def deleteRepoDir(repoDir: Path): Unit = {
    Try {
      Files.walkFileTree(repoDir, new SimpleFileVisitor[Path]() {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
          Files.delete(dir)
          FileVisitResult.CONTINUE
        }
      })
    }.failed.map { err =>
      _log.warn(s"Could not delete ${repoDir.toString}")
    }
  }

  // TODO: Make it async io
  def validateParent(oldCommit: Commit, newCommit: Commit, newCommitObject: TObject): Boolean = {
    val repoDir = setupFakeRepository()
    writeObjectToDisk(repoDir, newCommitObject)

    val newRefParentCommitT = Try {
      (new LibOstree).parentOf(repoDir.toAbsolutePath.toString, newCommit.get)
    }

    deleteRepoDir(repoDir)

    newRefParentCommitT match {
      case Success(newCommitParent) =>
        _log.info(s"new commit object parent is: $newCommitParent old ref is $oldCommit")

        newCommitParent == oldCommit.get
      case Failure(ex) =>
        _log.error("Error getting commit object parent", ex)
        false
    }
  }
}
