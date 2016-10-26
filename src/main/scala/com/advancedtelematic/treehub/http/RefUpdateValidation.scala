package com.advancedtelematic.treehub.http

import cats.data.Xor
import com.advancedtelematic.common.DigestCalculator
import com.advancedtelematic.data.DataType.{TObject, _}
import org.slf4j.LoggerFactory
import eu.timepit.refined.refineV

object RefUpdateValidation {
  val _log = LoggerFactory.getLogger(this.getClass)

  // TODO: This returns the first 32 bytes of the blob, which might be the msg if
  // it's an initial commit
  private def objectCommit(tObject: TObject): Xor[String, Commit] = {
    for {
      newCommitParent <- Xor.catchNonFatal(DigestCalculator.toHex(tObject.blob.slice(0, 32))).leftMap(_.getMessage)
      commit <- Xor.fromEither(refineV[ValidCommit](newCommitParent))
    } yield commit
  }

  def validateParent(oldCommit: Commit, newCommit: Commit, newCommitObject: TObject): Boolean = {
    objectCommit(newCommitObject) match {
      case Xor.Right(newCommitParent) =>
        _log.info(s"new commit object parent is: $newCommitParent old ref is $oldCommit")

        newCommitParent == oldCommit
      case Xor.Left(ex) =>
        _log.error("Error getting commit object parent", ex)
        false
    }
  }
}
