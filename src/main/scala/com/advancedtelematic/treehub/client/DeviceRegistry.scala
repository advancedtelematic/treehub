package com.advancedtelematic.treehub.client

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, ResponseEntity, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import com.advancedtelematic.libats.data.Namespace
import io.circe.Json
import org.slf4j.LoggerFactory
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

trait DeviceRegistryClient {
  def fetchNamespace(uuid: UUID): Future[Namespace]
}

class DeviceRegistryHttpClient(baseUri: Uri, mydeviceUri: Uri)
                              (implicit system: ActorSystem, mat: Materializer) extends DeviceRegistryClient {

  private val log = LoggerFactory.getLogger(this.getClass)

  private val http = Http()

  import system.dispatcher

  import cats.syntax.either._

  override def fetchNamespace(uuid: UUID): Future[Namespace] = {
    val req = HttpRequest(uri = baseUri.withPath(mydeviceUri.path / uuid.toString))
    execHttp[Json](req).flatMap { json =>
      Future.fromTry(json.hcursor.downField("namespace").as[String].map(Namespace.apply).toTry)
    }
  }

  private def execHttp[T](httpRequest: HttpRequest)
                         (implicit unmarshaller: Unmarshaller[ResponseEntity, T],
                          ec: ExecutionContext, ct: ClassTag[T]): Future[T] = {
    http.singleRequest(httpRequest).flatMap { response =>
      response.status match {
        case status if status.isSuccess() => unmarshaller(response.entity)
        case err => log.error(s"Got exception for request to ${httpRequest.uri}\n" +
          s"Error message: ${response.entity.toString}")
          FastFuture.failed(new Exception(err.toString))
      }
    }
  }
}