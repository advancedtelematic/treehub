import com.typesafe.sbt.SbtGit._
import com.typesafe.sbt.SbtGit.GitKeys._
import com.typesafe.sbt.GitVersioning
import scala.util.Try
import sbt._
import sbt.Keys._

object Versioning {
  lazy val settings = Seq(
    git.useGitDescribe := true
  )

  val Plugin = GitVersioning
}
