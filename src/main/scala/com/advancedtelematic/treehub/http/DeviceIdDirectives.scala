package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.{AuthorizationFailedRejection, Directive1, Directives, Rejection}
import cats.data.Xor
import com.advancedtelematic.jwt.JsonWebToken
import eu.timepit.refined._
import org.genivi.sota.data.Uuid
import org.genivi.sota.http.NsFromToken

object DeviceIdDirectives {
  import Directives._

  def fail[T](msg: String): Directive1[T] = extractLog.flatMap { l =>
    l.info(s"Could not extract namespace: $msg")
    reject(AuthorizationFailedRejection)
  }

  def removeSuffix(suffix: String): PartialFunction[String, String] ={
    case x if x.endsWith(suffix) => x.dropRight(suffix.length)
  }

  def fromScope(token: JsonWebToken): Xor[String, Uuid] = {
    val prefix = "ota-core."
    val tokens = token.scope.underlying.collect {
      case x if x.startsWith(prefix) => x.substring(prefix.length)
    }.collect(removeSuffix(".read").orElse(removeSuffix(".write")))

    if (tokens.size == 1) {
      refineV[Uuid.Valid](tokens.toVector(0)) match {
        case Left(msg) => Xor.Left(msg)
        case Right(uuid) => Xor.Right(Uuid(uuid))
      }
    } else {
      Xor.Left("Can't extract uuid from scopes")
    }

  }

  def extractFromToken: Directive1[Uuid] = {
    extractCredentials.flatMap {
      case Some(OAuth2BearerToken(serializedToken)) =>
        NsFromToken.parseToken[JsonWebToken](serializedToken).flatMap(fromScope _) match {
          case Xor.Right(uuid) => provide(uuid)
          case Xor.Left(msg) => fail(msg)
        }
      case _ => fail("No oauth token provided to extract namespace")
    }
  }

}
