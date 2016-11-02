/**
  * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
  * License: MPL-2.0
  */
package com.advancedtelematic.treehub.http

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.http.scaladsl.util.FastFuture
import akka.stream.ActorMaterializer
import com.advancedtelematic.data.DataType.{Commit, RefName}
import io.circe.generic.auto._
import org.genivi.sota.data.Namespace
import org.genivi.sota.http.NamespaceDirectives.nsHeader
import org.genivi.sota.marshalling.CirceMarshallingSupport

import scala.concurrent.{ExecutionContext, Future}

class CoreClient (baseUri: Uri, commitUri: Uri, treeHubUri: String)
                            (implicit system: ActorSystem, mat: ActorMaterializer) extends Core {

  import CirceMarshallingSupport._
  import HttpMethods._

  private val log = Logging(system, "org.genivi.sota.coreClient")

  private val http = akka.http.scaladsl.Http()

  case class ImageRequest(commit: Commit, ref: RefName, description: String, pullUri: Uri)

  def storeCommitInCore(ns: Namespace, commit: Commit, ref: RefName, description: String)
                       (implicit ec: ExecutionContext): Future[Unit] = {
    for {
      entity   <- Marshal(ImageRequest(commit, ref, ref.get, treeHubUri)).to[MessageEntity]
      req      =  HttpRequest(method = POST, uri = baseUri.withPath(commitUri.path), entity = entity)
      response <- execHttp[Unit](req.withHeaders(nsHeader(ns)))
    } yield response
  }

  private def execHttp[T](httpRequest: HttpRequest)
                         (implicit unmarshaller: Unmarshaller[ResponseEntity, T],
                          ec: ExecutionContext): Future[T] = {
    http.singleRequest(httpRequest).flatMap { response =>
      response.status match {
        case status if status.isSuccess() => unmarshaller(response.entity)
        case err => FastFuture.failed(new Exception(err.toString))
      }
    }
  }
}
