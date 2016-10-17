package com.advancedtelematic.ota_treehub.db

import slick.driver.MySQLDriver.api._

object Schema {
  import SlickAnyVal._
  import org.genivi.sota.refined.SlickRefined._
  import com.advancedtelematic.data.DataType._

  class TObjectTable(tag: Tag) extends Table[TObject](tag, "object") {
    def id = column[ObjectId]("id", O.PrimaryKey)
    def blob = column[Array[Byte]]("blob")

    override def * = (id, blob) <> ((TObject.apply _).tupled, TObject.unapply)
  }

  val objects = TableQuery[TObjectTable]

  // TODO: FK to objects
  case class RefTable(tag: Tag) extends Table[Ref](tag, "ref") {
    def name = column[RefName]("name", O.PrimaryKey)
    def value = column[Commit]("value")

    override def * = (name, value) <> ((Ref.apply _).tupled, Ref.unapply)
  }

  protected[db] val refs = TableQuery[RefTable]
}
