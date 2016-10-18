package com.advancedtelematic.treehub.db

import shapeless.{::, Generic, HNil}
import slick.driver.MySQLDriver.api._

import scala.reflect.ClassTag

object SlickAnyVal {
  implicit def dbSerializableAnyValMapping[T <: AnyVal]
  (implicit unwrapper: Generic.Aux[T, String :: HNil], classTag: ClassTag[T]): BaseColumnType[T] =
    MappedColumnType.base[T, String](
      (v: T) => unwrapper.to(v).head,
      (s: String) => unwrapper.from(s :: HNil)
    )
}
