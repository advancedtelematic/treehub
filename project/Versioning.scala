import com.typesafe.sbt.SbtGit._
import com.typesafe.sbt.SbtGit.GitKeys._
import com.typesafe.sbt.GitVersioning
import com.typesafe.sbt.git.ConsoleGitRunner

import scala.util.Try
import sbt._
import sbt.Keys._

object Versioning {
  /**
    * Currently there is a problem with JGit where it returns a wrong
    * git describe version, so we use git directly
    */
  lazy val settings = Seq(
    git.runner := ConsoleGitRunner,
    git.useGitDescribe := true,
    git.baseVersion := "0.0.1",
    git.gitUncommittedChanges := false
  )

  val Plugin = GitVersioning
}
