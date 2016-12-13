package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.data.DataType.Ref
import org.genivi.sota.http.Errors.{MissingEntity, RawError}
import org.genivi.sota.rest.ErrorCode

object ErrorCodes {
  val CommitMissing = ErrorCode("commit_missing")
  val BlobNotFound = ErrorCode("blob_missing")
  val ObjectNotFound = ErrorCode("object_missing")
  val ObjectAExists = ErrorCode("object_exists")
}

object Errors {
  val CommitMissing = RawError(ErrorCodes.CommitMissing, StatusCodes.PreconditionFailed, "Commit does not exist")
  val BlobNotFound =  RawError(ErrorCodes.BlobNotFound, StatusCodes.NotFound, "object blob not stored")
  val ObjectNotFound = RawError(ErrorCodes.ObjectNotFound, StatusCodes.NotFound, "object not found")
  val ObjectExists =  RawError(ErrorCodes.ObjectAExists, StatusCodes.Conflict, "object already exists")
}
