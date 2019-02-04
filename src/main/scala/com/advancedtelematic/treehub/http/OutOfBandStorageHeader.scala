package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model.headers._

import scala.util.{Failure, Success, Try}


object OutOfBandStorageHeader extends ModeledCustomHeaderCompanion[OutOfBandStorageHeader] {
  override def name: String = "x-ats-accept-redirect"

  override def parse(value: String): Try[OutOfBandStorageHeader] =
    if(value == "true")
      Success(new OutOfBandStorageHeader)
    else
      Failure(new IllegalArgumentException(s"invalid value for header $name, valid values: true"))
}

class OutOfBandStorageHeader extends ModeledCustomHeader[OutOfBandStorageHeader] {
  override def companion: ModeledCustomHeaderCompanion[OutOfBandStorageHeader] = OutOfBandStorageHeader

  override def value(): String = "true"

  override def renderInRequests(): Boolean = true

  override def renderInResponses(): Boolean = true
}
