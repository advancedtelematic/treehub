package com.advancedtelematic.util

import com.advancedtelematic.libats.data.Namespace
import com.advancedtelematic.treehub.Settings
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSuite, Matchers}

abstract class TreeHubSpec extends FunSuite with Matchers with ScalaFutures with Settings {
  val defaultNs = Namespace("default")
}
