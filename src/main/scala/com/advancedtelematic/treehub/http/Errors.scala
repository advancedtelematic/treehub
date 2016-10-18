package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model.StatusCodes
import org.genivi.sota.http.Errors.RawError
import org.genivi.sota.rest.ErrorCode

object ErrorCodes {
  val CommitMissing = ErrorCode("commit_missing")
}

object Errors {
  val CommitMissing = RawError(ErrorCodes.CommitMissing, StatusCodes.PreconditionFailed, "Commit does not exist")
}
