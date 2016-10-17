package com.advancedtelematic.ota_treehub.http

import akka.http.scaladsl.model.StatusCodes
import org.genivi.sota.http.Errors.RawError
import org.genivi.sota.rest.ErrorCode

object ErrorCodes {
  val ObjectIdMismatch = ErrorCode("object_id_checksum_mismatch")
}

object Errors {
  import ErrorCodes._
  val ObjectIdMismatchError = RawError(ObjectIdMismatch, StatusCodes.PreconditionFailed, "Blob checksum does not match object id")
}
