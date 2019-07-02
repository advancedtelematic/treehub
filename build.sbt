name := "treehub"
organization := "com.advancedtelematic.com"
scalaVersion := "2.12.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += "ATS Releases" at "http://nexus.advancedtelematic.com:8081/content/repositories/releases"

resolvers += "ATS Snapshots" at "http://nexus.advancedtelematic.com:8081/content/repositories/snapshots"

resolvers += "commons-logging-empty" at "http://version99.qos.ch"

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
  .settings(Seq(libraryDependencies ++= {
    val akkaV = "2.5.20"
    val akkaHttpV = "10.1.7"
    val scalaTestV = "3.0.0"
    val libatsV = "0.3.0-16-gaa02547"

    Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaV,
      "com.typesafe.akka" %% "akka-stream" % akkaV,
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaV % "test",
      "com.typesafe.akka" %% "akka-http" % akkaHttpV,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test",
      "com.typesafe.akka" %% "akka-slf4j" % akkaV,
      "org.scalatest"     %% "scalatest" % scalaTestV % "test,it",

      "ch.qos.logback" % "logback-classic" % "1.1.3",
      "org.slf4j" % "slf4j-api" % "1.7.16",

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

dockerRepository in Docker := Some("advancedtelematic")

packageName in Docker := packageName.value

dockerUpdateLatest in Docker := true

defaultLinuxInstallLocation in Docker := s"/opt/${moduleName.value}"

dockerCommands := Seq(
  Cmd("FROM", "advancedtelematic/alpine-jre:8u191-jre-alpine3.9"),
  Cmd("RUN", "apk update && apk add --update bash coreutils"),
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

