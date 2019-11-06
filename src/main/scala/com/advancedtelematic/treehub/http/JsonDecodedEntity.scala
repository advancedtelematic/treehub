package com.advancedtelematic.treehub.http

import io.circe.{Decoder, Json}

case class JsonDecodedEntity[T : Decoder](json: Json, entity: T)

object JsonDecodedEntity {
  implicit def jsonEntityDecoder[T](implicit decoder: Decoder[T]): Decoder[JsonDecodedEntity[T]] =
    Decoder.decodeJson.emapTry { json =>
      json.as[T].map { obj =>
        JsonDecodedEntity(json, obj)
      }.toTry
    }
}