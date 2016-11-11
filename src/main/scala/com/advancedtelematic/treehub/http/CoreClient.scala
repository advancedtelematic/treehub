/**
  * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
  * License: MPL-2.0
  */
package com.advancedtelematic.treehub.http

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.http.scaladsl.util.FastFuture
import akka.stream.ActorMaterializer
import com.advancedtelematic.data.DataType.{Commit, RefName}
import io.circe.generic.auto._
import io.circe.syntax._
import org.genivi.sota.data.Namespace
import org.genivi.sota.http.NamespaceDirectives.nsHeader
import org.genivi.sota.marshalling.CirceMarshallingSupport

import scala.concurrent.{ExecutionContext, Future}

class CoreClient(baseUri: Uri, packagesUri: Uri, treeHubUri: String)
                (implicit system: ActorSystem, mat: ActorMaterializer) extends Core {

  import CirceMarshallingSupport._
  import HttpMethods._

  private val log = Logging(system, "org.genivi.sota.coreClient")

  private val http = akka.http.scaladsl.Http()

  sealed case class ImageRequest(commit: Commit, refName: RefName, description: String, pullUri: String)

  def storeCommitInCore(ns: Namespace, commit: Commit, ref: RefName, description: String)
                       (implicit ec: ExecutionContext): Future[Unit] = {
    val fileContents = ImageRequest(commit, ref, description, treeHubUri).asJson.noSpaces
    val bodyPart = BodyPart.Strict("file", HttpEntity(fileContents), Map("fileName" -> ref.get))
    //TODO: PRO-1883 Add major/minor version for package
    val uri = baseUri.withPath(packagesUri.path + s"/treehub-${ref.get}/${commit.get}")
    val req = HttpRequest(method = PUT, uri = uri, entity = Multipart.FormData(bodyPart).toEntity())
    execHttp[Unit](req.addHeader(nsHeader(ns)))
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
