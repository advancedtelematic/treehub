package com.advancedtelematic.ota_treehub.http

import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.ByteString
import com.advancedtelematic.common.DigestCalculator

import scala.concurrent.{ExecutionContext, Future}

object ObjectUpload {
  def readFile(content: Source[ByteString, _])(implicit ec: ExecutionContext, mat: Materializer): Future[(String, ByteString)] = {
    val (digestF, contentF) =
      content
        .alsoToMat(DigestCalculator())(Keep.right)
        .toMat(Sink.reduce[ByteString](_ ++ _))(Keep.both)
        .run()

    for {
      digest <- digestF
      content <- contentF
    } yield (digest, content)
  }
}
