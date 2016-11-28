/**
  * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
  * License: MPL-2.0
  */
package com.advancedtelematic.treehub.client

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.http.scaladsl.util.FastFuture
import akka.stream.ActorMaterializer
import com.advancedtelematic.data.DataType.{Commit, Ref, RefName}
import io.circe.generic.auto._
import io.circe.syntax._
import org.genivi.sota.http.NamespaceDirectives.nsHeader
import org.genivi.sota.marshalling.CirceMarshallingSupport

import scala.concurrent.{ExecutionContext, Future}

class CoreClient(baseUri: Uri, packagesUri: Uri, treeHubUri: String)
                (implicit system: ActorSystem, mat: ActorMaterializer) extends Core {

  import CirceMarshallingSupport._
  import HttpMethods._

  private val http = akka.http.scaladsl.Http()

  sealed case class ImageRequest(commit: Commit, refName: RefName, description: String, pullUri: String)

  def publishRef(ref: Ref, description: String)
                (implicit ec: ExecutionContext): Future[Unit] = {
    val fileContents = ImageRequest(ref.value, ref.name, description, treeHubUri).asJson.noSpaces
    val bodyPart = BodyPart.Strict("file", HttpEntity(fileContents), Map("fileName" -> ref.name.get))
    val formattedRefName = ref.name.get.replaceFirst("^heads/", "").replace("/", "-")
    val uri = baseUri.withPath(packagesUri.path + s"/treehub-$formattedRefName/${ref.value.get}")
                     .withQuery(Query("description" -> description))
    val req = HttpRequest(method = PUT, uri = uri, entity = Multipart.FormData(bodyPart).toEntity())
    execHttp[Unit](req.addHeader(nsHeader(ref.namespace)))
  }

  private def execHttp[T](httpRequest: HttpRequest)
                         (implicit unmarshaller: Unmarshaller[ResponseEntity, T], ec: ExecutionContext): Future[T] =
    http.singleRequest(httpRequest).flatMap { response =>
      response.status match {
        case status if status.isSuccess() => unmarshaller(response.entity)
        case err => FastFuture.failed(new Exception(err.toString))
      }
    }
}
