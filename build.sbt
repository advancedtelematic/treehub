name := "treehub"
organization := "com.advancedtelematic.com"
scalaVersion := "2.12.8"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += "ATS Releases" at "https://nexus.ota.here.com/content/repositories/releases"

resolvers += "ATS Snapshots" at "https://nexus.ota.here.com/content/repositories/snapshots"

resolvers += "commons-logging-empty" at "https://version99.qos.ch"

def itFilter(name: String): Boolean = name endsWith "IntegrationSpec"

def unitFilter(name: String): Boolean = !itFilter(name)

lazy val ItTest = config("it").extend(Test)

lazy val UnitTest = config("ut").extend(Test)

lazy val root = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .configs(ItTest)
  .settings(inConfig(ItTest)(Defaults.testTasks): _*)
  .configs(UnitTest)
  .settings(inConfig(UnitTest)(Defaults.testTasks): _*)
  .settings(testOptions in UnitTest := Seq(Tests.Filter(unitFilter)))
  .settings(testOptions in IntegrationTest := Seq(Tests.Filter(itFilter)))
  .settings(sonarSettings)
  .settings(Seq(libraryDependencies ++= {
    val akkaV = "2.5.25"
    val akkaHttpV = "10.1.10"
    val scalaTestV = "3.0.8"
    val libatsV = "0.3.0-109-ge12f057"

    Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaV,
      "com.typesafe.akka" %% "akka-stream" % akkaV,
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaV % "test",
      "com.typesafe.akka" %% "akka-http" % akkaHttpV,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test",
      "com.typesafe.akka" %% "akka-slf4j" % akkaV,
      "org.scalatest"     %% "scalatest" % scalaTestV % "test,it",

      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "org.slf4j" % "slf4j-api" % "1.7.25",

      "com.advancedtelematic" %% "libats" % libatsV,
      "com.advancedtelematic" %% "libats-http" % libatsV,
      "com.advancedtelematic" %% "libats-http-tracing" % libatsV,
      "com.advancedtelematic" %% "libats-messaging" % libatsV,
      "com.advancedtelematic" %% "libats-messaging-datatype" % libatsV,
      "com.advancedtelematic" %% "libats-auth" % libatsV,
      "com.advancedtelematic" %% "libats-slick" % libatsV,
      "com.advancedtelematic" %% "libats-metrics-akka" % libatsV,
      "com.advancedtelematic" %% "libats-metrics-prometheus" % libatsV,
      "com.advancedtelematic" %% "libats-logging" % libatsV,
      "com.advancedtelematic" %% "libats-logging" % libatsV,

      "org.scala-lang.modules" %% "scala-async" % "0.9.6",
      "org.mariadb.jdbc" % "mariadb-java-client" % "1.4.4",

      "com.amazonaws" % "aws-java-sdk-s3" % "1.11.86"
    )
  }))

mainClass in Compile := Some("com.advancedtelematic.treehub.Boot")

buildInfoOptions += BuildInfoOption.ToMap

buildInfoOptions += BuildInfoOption.BuildTime

import com.typesafe.sbt.packager.docker._

dockerRepository := Some("advancedtelematic")

packageName in Docker := packageName.value

dockerUpdateLatest := true

dockerAliases ++= Seq(dockerAlias.value.withTag(git.gitHeadCommit.value))

defaultLinuxInstallLocation in Docker := s"/opt/${moduleName.value}"

dockerCommands := Seq(
  Cmd("FROM", "advancedtelematic/alpine-jre:adoptopenjdk-jre8u262-b10"),
  ExecCmd("RUN", "mkdir", "-p", s"/var/log/${moduleName.value}"),
  Cmd("ADD", "opt /opt"),
  Cmd("WORKDIR", s"/opt/${moduleName.value}"),
  ExecCmd("ENTRYPOINT", s"/opt/${moduleName.value}/bin/${moduleName.value}"),
  Cmd("RUN", s"chown -R daemon:daemon /opt/${moduleName.value}"),
  Cmd("RUN", s"chown -R daemon:daemon /var/log/${moduleName.value}"),
  Cmd("USER", "daemon")
)

enablePlugins(JavaAppPackaging)

Revolver.settings

Versioning.settings

Release.settings

enablePlugins(Versioning.Plugin)

lazy val sonarSettings = Seq(
  sonarProperties ++= Map(
    "sonar.projectName" -> "OTA Connect Treehub",
    "sonar.projectKey" -> "ota-connect-treehub",
    "sonar.host.url" -> "https://sonar7.devtools.in.here.com",
    "sonar.links.issue" -> "https://saeljira.it.here.com/projects/OTA/issues",
    "sonar.links.scm" -> "https://main.gitlab.in.here.com/olp/edge/ota/connect/back-end/treehub",
    "sonar.links.ci" -> "https://main.gitlab.in.here.com/olp/edge/ota/connect/back-end/treehub/pipelines",
    "sonar.java.binaries" -> "./target/scala-*/classes", 
    "sonar.scala.coverage.reportPaths"->"target/scala-2.12/scoverage-report/scoverage.xml",
    "sonar.language" -> "scala",
    "sonar.projectVersion" -> version.value,
  )
)
