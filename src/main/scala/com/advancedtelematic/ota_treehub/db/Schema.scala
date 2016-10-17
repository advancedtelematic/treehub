package com.advancedtelematic.ota_treehub.db

import slick.driver.MySQLDriver.api._

object Schema {
  import SlickAnyVal._
  import org.genivi.sota.refined.SlickRefined._
  import com.advancedtelematic.data.DataType._

  class TObjectTable(tag: Tag) extends Table[TObject](tag, "object") {
    def id = column[ObjectId]("object_id", O.PrimaryKey)
    def blob = column[Array[Byte]]("blob")

    override def * = (id, blob) <> ((TObject.apply _).tupled, TObject.unapply)
  }

  val objects = TableQuery[TObjectTable]

  case class RefTable(tag: Tag) extends Table[Ref](tag, "ref") {
    def name = column[RefName]("name", O.PrimaryKey)
    def value = column[Commit]("value")
    def objectId = column[ObjectId]("object_id")

    def fk = foreignKey("fk_ref_object", objectId, objects)(_.id)

    override def * = (name, value, objectId) <> ((Ref.apply _).tupled, Ref.unapply)
  }

  protected[db] val refs = TableQuery[RefTable]
}
