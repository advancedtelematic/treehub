package com.advancedtelematic.data

import java.nio.file.{Path, Paths}

import cats.data.Xor
import com.advancedtelematic.common.DigestCalculator
import eu.timepit.refined.api.{Refined, Validate}
import org.genivi.sota.data.Namespace
import eu.timepit.refined.refineV

object DataType {
  import ValidationUtils._

  case class ValidCommit()

  type Commit = Refined[String, ValidCommit]

  implicit val validCommit: Validate.Plain[String, ValidCommit] =
    Validate.fromPredicate(
      hash => validHex(64, hash),
      hash => s"$hash is not a sha-256 commit hash",
      ValidCommit()
    )

  object Commit {
    def from(bytes: Array[Byte]): Xor[String, Commit] = {
      val commitE = refineV[ValidCommit](DigestCalculator.byteDigest()(bytes))
      Xor.fromEither(commitE)
    }
  }

  case class Ref(namespace: Namespace, name: RefName, value: Commit, objectId: ObjectId)

  case class RefName(get: String) extends AnyVal

  case class ValidObjectId()

  implicit val validObjectId: Validate.Plain[String, ValidObjectId] =
    Validate.fromPredicate(
      objectId => {
        val (sha, objectType) = objectId.splitAt(objectId.indexOf('.'))
        validHex(64, sha) && objectType.nonEmpty
      },
      objectId => s"$objectId must be in format <sha256>.objectType",
      ValidObjectId()
    )

  type ObjectId = Refined[String, ValidObjectId]

  implicit class ObjectIdOps(value: ObjectId) {
    def path(parent: Path): Path = {
      val (prefix, rest) = value.get.splitAt(2)
      Paths.get(parent.toString, prefix, rest)
    }

    def filename: Path = path(Paths.get("/")).getFileName
  }

  object ObjectId {
    def from(commit: Commit): ObjectId = ObjectId.parse(commit.get + ".commit").toEither.right.get

    def parse(string: String): Xor[String, ObjectId] = Xor.fromEither(refineV[ValidObjectId](string))
  }

  case class TObject(namespace: Namespace, id: ObjectId, byteSize: Long)
}

protected[data] object ValidationUtils {
  def validHex(length: Long, str: String): Boolean = {
    str.length == length && str.forall(h => ('0' to '9').contains(h) || ('a' to 'f').contains(h))
  }
}