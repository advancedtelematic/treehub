package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.data.DataType.ObjectId
import com.advancedtelematic.libats.data.ErrorCode
import com.advancedtelematic.libats.http.Errors.{JsonError, RawError}
import com.advancedtelematic.libats.http.Errors.Error

object ErrorCodes {
  val CommitMissing = ErrorCode("commit_missing")
  val BlobNotFound = ErrorCode("blob_missing")
  val ObjectNotFound = ErrorCode("object_missing")
  val ObjectAExists = ErrorCode("object_exists")
  val SummaryDoesNotExist = ErrorCode("summary_missing")
  val StaticDeltaDoesNotExist = ErrorCode("delta_missing")
}

object Errors {
  case class ObjectExists(id: ObjectId) extends Error(ErrorCodes.ObjectAExists, StatusCodes.Conflict, s"$id already exists")

  val CommitMissing = RawError(ErrorCodes.CommitMissing, StatusCodes.PreconditionFailed, "Commit does not exist")
  val BlobNotFound =  RawError(ErrorCodes.BlobNotFound, StatusCodes.NotFound, "object blob not stored")
  val ObjectNotFound = RawError(ErrorCodes.ObjectNotFound, StatusCodes.NotFound, "object not found")
  val SummaryDoesNotExist = RawError(ErrorCodes.ObjectAExists, StatusCodes.NotFound, "summary does not exist")
  val StaticDeltaDoesNotExist = RawError(ErrorCodes.ObjectAExists, StatusCodes.NotFound, "delta does not exist")
}
