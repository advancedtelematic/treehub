package com.advancedtelematic.treehub.http

import akka.util.ByteString
import com.advancedtelematic.common.DigestCalculator
import com.advancedtelematic.data.DataType.{TObject, _}
import eu.timepit.refined.boolean.Xor
import org.slf4j.LoggerFactory
import eu.timepit.refined.refineV
import cats.syntax.either._

object RefUpdateValidation {
  val _log = LoggerFactory.getLogger(this.getClass)

  // TODO: This returns the first 32 bytes of the blob, which might be the msg if
  // it's an initial commit
  private def objectCommit(blob: ByteString): Either[String, Commit] = {
    for {
      newCommitParent <- Either.catchNonFatal(DigestCalculator.toHex(blob.slice(0, 32).toArray)).leftMap(_.getMessage)
      commit <- refineV[ValidCommit](newCommitParent)
    } yield commit
  }

  def validateParent(oldCommit: Commit, newCommit: Commit, newCommitBlob: ByteString): Boolean = {
    objectCommit(newCommitBlob) match {
      case Right(newCommitParent) =>
        _log.info(s"new commit object parent is: $newCommitParent old ref is $oldCommit")

        newCommitParent == oldCommit
      case Left(ex) =>
        _log.error("Error getting commit object parent", ex)
        false
    }
  }
}
