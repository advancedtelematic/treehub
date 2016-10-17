package com.advancedtelematic.ota_treehub.db

import com.advancedtelematic.data.DataType.Commit
import eu.timepit.refined.api.{Refined, Validate}
import slick.driver.MySQLDriver.api._
import shapeless._

import scala.reflect.ClassTag


object SlickAnyVal {
  implicit def dbSerializableAnyValMapping[T <: AnyVal]
  (implicit unwrapper: Generic.Aux[T, String :: HNil], classTag: ClassTag[T]): BaseColumnType[T] =
    MappedColumnType.base[T, String](
      (v: T) => unwrapper.to(v).head,
      (s: String) => unwrapper.from(s :: HNil)
    )
}


object Schema {
  import SlickAnyVal._
  import org.genivi.sota.refined.SlickRefined._

  case class TObject(id: String, blob: Array[Byte])

  class TObjectTable(tag: Tag) extends Table[TObject](tag, "object") {
    def id = column[String]("id", O.PrimaryKey)
    def blob = column[Array[Byte]]("blob")

    override def * = (id, blob) <> ((TObject.apply _).tupled, TObject.unapply)
  }

  val objects = TableQuery[TObjectTable]

  case class Ref(name: RefName, value: Commit)

  case class RefName(get: String) extends AnyVal

  // TODO: FK to objects
  case class RefTable(tag: Tag) extends Table[Ref](tag, "ref") {
    def name = column[RefName]("name", O.PrimaryKey)
    def value = column[Commit]("value")

    override def * = (name, value) <> ((Ref.apply _).tupled, Ref.unapply)
  }

  protected[db] val refs = TableQuery[RefTable]
}
