package com.advancedtelematic.treehub.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.{Directives, _}
import akka.stream.{ActorMaterializer, Materializer}
import org.genivi.sota.data.Namespace
import org.genivi.sota.http.{AuthedNamespaceScope, NamespaceDirectives, TokenValidator}


object Http {
  import Directives._

  private lazy val extractNamespaceFromHeader =
    optionalHeaderValueByName("x-ats-namespace").map(_.map(ns => AuthedNamespaceScope(Namespace(ns))))

  lazy val extractNamespace: Directive1[AuthedNamespaceScope] = {
    val authNamespaceExtractor = NamespaceDirectives.fromConfig()

    extractNamespaceFromHeader.flatMap {
      case Some(ns) => provide(ns)
      case None => authNamespaceExtractor
    }
  }

  // TODO: Should be Materializer instead of ActorMaterializer
  def tokenValidator(implicit s: ActorSystem, mat: ActorMaterializer): Directive0 = TokenValidator().fromConfig

  def transformAtsAuthHeader: Directive0 = mapRequest { req ⇒
    req.mapHeaders { headers ⇒
      val atsAuthHeader = headers.find(_.is("x-ats-authorization"))

      atsAuthHeader match {
        case Some(h) ⇒
          val oauthToken = h.value().replace("Bearer ", "")
          headers :+ Authorization(OAuth2BearerToken(oauthToken))
        case None ⇒ headers
      }
    }
  }
}
