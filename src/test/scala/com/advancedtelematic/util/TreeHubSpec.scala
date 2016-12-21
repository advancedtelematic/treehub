package com.advancedtelematic.util

import com.typesafe.config.ConfigFactory
import org.genivi.sota.data.Namespace
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSuite, Matchers}

abstract class TreeHubSpec extends FunSuite with Matchers with ScalaFutures {
  val defaultNs = Namespace("default")
  val config = ConfigFactory.load()
}
