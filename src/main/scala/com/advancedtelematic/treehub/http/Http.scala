package com.advancedtelematic.treehub.http

import akka.http.scaladsl.server.{Directives, _}
import com.advancedtelematic.libats.auth.NamespaceDirectives
import com.advancedtelematic.libats.data.DataType.Namespace
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
}
