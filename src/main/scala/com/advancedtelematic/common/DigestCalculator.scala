/*
 * Copyright (C) 2016 HERE Global B.V.
 *
 * Licensed under the Mozilla Public License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.mozilla.org/en-US/MPL/2.0/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: MPL-2.0
 * License-Filename: LICENSE
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
