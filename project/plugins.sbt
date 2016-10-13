addSbtPlugin("io.spray" % "sbt-revolver" % "0.8.0")

addSbtPlugin("org.flywaydb" % "flyway-sbt" % "4.0.3")

resolvers += "Flyway" at "https://flywaydb.org/repo"

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.6.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.4")

addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.8.5")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.3")
