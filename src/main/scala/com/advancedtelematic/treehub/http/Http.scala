package com.advancedtelematic.treehub.http

import akka.actor.ActorSystem
import akka.http.scaladsl.server.directives.Credentials.{Missing, Provided}
import akka.http.scaladsl.server.{Directives, _}
import akka.stream.ActorMaterializer
import org.genivi.sota.common.DeviceRegistry
import org.genivi.sota.data.Namespace
import org.genivi.sota.http.{NamespaceDirectives, TokenValidator}

import scala.concurrent.ExecutionContext

object Http {
  import Directives._

  protected[http] val PASSWORD = "quochai1ech5oot5gaeJaifooqu6Saew"

  lazy val namespaceDirective = NamespaceDirectives.fromConfig()

  def deviceNamespace(deviceRegistry: DeviceRegistry)
                     (implicit ec: ExecutionContext): Directive1[Namespace] = {
    DeviceIdDirectives.extractFromToken.flatMap { deviceId =>
      onSuccess(deviceRegistry.fetchMyDevice(deviceId).map(_.namespace))
    }
  }

  def extractNamespace(apiVersion: String, allowEmpty: Boolean = false): Directive1[Namespace] = {
    apiVersion match {
      case "v1" => optionalHeaderValueByName("x-ats-namespace").flatMap {
        case Some(h) =>
          val (user, pass) = h.splitAt(h.indexOf("|"))
          if(pass == "|" + PASSWORD)
            provide(Namespace(user))
          else
            reject(AuthorizationFailedRejection)
        case None =>
          authenticateBasic[Namespace]("treehub", {
            case p @ Provided(c) if p.verify(PASSWORD) => Some(Namespace(c))
            case Missing if allowEmpty => Some(Namespace("default"))
            case _ => None
          })
      }
      case _ => namespaceDirective
    }
  }


  def tokenValidator(implicit system: ActorSystem, mat: ActorMaterializer): String => Directive0 = {
    lazy val tokenValidator = TokenValidator().fromConfig()

    apiVersion => apiVersion match {
      case "v1" => pass
      case _ => tokenValidator
    }
  }
}
