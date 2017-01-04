package com.advancedtelematic.util

import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Millis, Seconds, Span}

trait DefaultPatience extends PatienceConfiguration {
  override implicit def patienceConfig = PatienceConfig(timeout = Span(10, Seconds), interval = Span(500, Millis))
}
