/**
 * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
 * License: MPL-2.0
 */
package com.advancedtelematic.common

import java.security.MessageDigest

import akka.stream.scaladsl.Sink
import akka.util.ByteString

import scala.concurrent.{ExecutionContext, Future}

object DigestCalculator {
  type DigestResult = String

  private lazy val DEFAULT_ALGORITHM = "SHA-256"

  def toHex(bytes: Array[Byte]) = bytes.map("%02X".format(_)).mkString.toLowerCase

  def byteDigest(algorithm: String = DEFAULT_ALGORITHM)(bytes: Array[Byte]): String = {
    val digest = MessageDigest.getInstance(algorithm)
    digest.update(bytes)
    toHex(digest.digest())
  }

  def digest(algorithm: String = DEFAULT_ALGORITHM)(str: String): String = {
    byteDigest(algorithm)(str.getBytes)
  }

  def apply(algorithm: String = DEFAULT_ALGORITHM)(implicit ec: ExecutionContext): Sink[ByteString, Future[DigestResult]] = {
    val digest = MessageDigest.getInstance(algorithm)

    Sink.fold(digest) { (d, b: ByteString) =>
      d.update(b.toArray)
      d
    } mapMaterializedValue(_.map(dd => toHex(dd.digest())))
  }
}
