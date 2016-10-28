package com.advancedtelematic.treehub.http

import akka.http.scaladsl.server.directives.Credentials.{Missing, Provided}
import akka.http.scaladsl.server.{Directives, _}
import org.genivi.sota.data.Namespace

object Http {
  import Directives._

  protected[http] val PASSWORD = "quochai1ech5oot5gaeJaifooqu6Saew"

  def extractNamespace(allowEmpty: Boolean = false): Directive1[Namespace] = {
    optionalHeaderValueByName("x-ats-namespace").flatMap {
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
  }
}
