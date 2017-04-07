package com.advancedtelematic.treehub.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.{Directives, _}
import akka.stream.{ActorMaterializer, Materializer}
import com.advancedtelematic.libats.auth.{NamespaceDirectives, TokenValidator}
import com.advancedtelematic.libats.data.Namespace
import com.advancedtelematic.treehub.client.DeviceRegistryClient
import scala.concurrent.ExecutionContext

object Http {
  import Directives._

  lazy val extractNamespace = NamespaceDirectives.fromConfig()

  def deviceNamespace(deviceRegistry: DeviceRegistryClient)
                     (implicit ec: ExecutionContext): Directive1[Namespace] = {
    DeviceIdDirectives.extractFromToken.flatMap { deviceId =>
      onSuccess(deviceRegistry.fetchNamespace(deviceId))
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
