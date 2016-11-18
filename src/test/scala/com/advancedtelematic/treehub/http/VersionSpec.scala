package com.advancedtelematic.treehub.http

import org.scalatest.{FunSuite, Matchers}

class VersionSpec extends FunSuite with Matchers {

  val baseVersion = "0.0.1"
  val baseVersionIncremented = "0.0.2"
  val newVersion = "0.0.4"
  val newVersionIncremented = "0.0.5"

  val majorBaseVersion = "1.0.5"
  val majorNewVersion = "2.0.0"
  val majorNewVersionIncremented = "2.0.1"

  test("no parameter returns incremented formerVersion") {
    Version.get(baseVersion, None) shouldBe Some(baseVersionIncremented)
  }

  test("new version takes precedence over formerVersion") {
    Version.get(baseVersion, Some(newVersion)) shouldBe Some(newVersionIncremented)
  }

  test("new major version takes precedence over formerVersion") {
    Version.get(majorBaseVersion, Some(majorNewVersion)) shouldBe Some(majorNewVersionIncremented)
  }

  test("new version older than formerVersion fails") {
    Version.get(newVersion, Some(baseVersion)) shouldBe None
  }

  test("invalid new version fails") {
    Version.get(baseVersion, Some("invalid")) shouldBe None
  }

  test("invalid former version fails") {
    Version.get("invalid", Some(baseVersion)) shouldBe None
  }
}
