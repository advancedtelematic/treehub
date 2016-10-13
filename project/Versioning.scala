import com.typesafe.sbt.SbtGit._
import com.typesafe.sbt.SbtGit.GitKeys._
import com.typesafe.sbt.GitVersioning
import scala.util.Try
import sbt._
import sbt.Keys._

object Versioning {
  /**
    * Currently there is a problem with JGit where it returns a wrong
    * git describe version, so we use git directly
    */
  lazy val consoleGitDescribe = Try("git describe".!!).toOption.map(_.trim)

  lazy val settings = Seq(
    git.useGitDescribe := true,
    git.baseVersion := "0.0.1",
    git.gitDescribedVersion := consoleGitDescribe
      orElse gitReader.value.withGit(_.describedVersion)
      map(_.drop(1))
      orElse formattedShaVersion.value
      orElse Some(git.baseVersion.value)
  )

  val Plugin = GitVersioning
}
