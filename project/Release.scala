
import com.typesafe.sbt.SbtGit.GitKeys._
import com.typesafe.sbt.packager.SettingsHelper._
import sbtrelease._
import sbtrelease.ReleaseStateTransformations.{setReleaseVersion => _, _}
import sbt.Keys._
import sbt._
import com.typesafe.sbt.SbtNativePackager.Docker

import sbtrelease.ReleasePlugin.autoImport._

object Release {

  def setVersionOnly(selectVersion: Versions => String): ReleaseStep = { st: State =>
    val vs = st.get(ReleaseKeys.versions).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))
    val selected = selectVersion(vs)

    st.log.info("Setting version to '%s'." format selected)
    val useGlobal = Project.extract(st).get(releaseUseGlobalVersion)
    val versionStr = (if (useGlobal) globalVersionString else versionString) format selected

    reapply(Seq(
      if (useGlobal) version in ThisBuild := selected
      else version := selected
    ), st)
  }

  lazy val setReleaseVersion: ReleaseStep = setVersionOnly(_._1)

  lazy val settings = {
    releaseVersion <<= (releaseVersionBump) (bumper => {
      ver => Version(ver)
        .map(_.withoutQualifier)
        .map(_.bump(bumper).string).getOrElse(versionFormatError)
    })

    val showNextVersion = settingKey[String]("the future version once releaseNextVersion has been applied to it")
    val showReleaseVersion = settingKey[String]("the future version once releaseNextVersion has been applied to it")

    Seq(
      showReleaseVersion <<= (version, releaseVersion) ((v, f) => f(v)),
      showNextVersion <<= (version, releaseNextVersion) ((v, f) => f(v)),

      releaseProcess := Seq(
        checkSnapshotDependencies,
        inquireVersions,
        setReleaseVersion,
        tagRelease,
        ReleaseStep(releaseStepTask(publish in Docker)),
        pushChanges
      ),
      releaseIgnoreUntrackedFiles := true
    )
  }
}
