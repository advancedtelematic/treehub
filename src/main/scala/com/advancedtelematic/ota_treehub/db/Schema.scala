package com.advancedtelematic.ota_treehub.db

import slick.driver.MySQLDriver.api._

object Schema {
  case class TObject(id: String, blob: Array[Byte])

  class TObjectTable(tag: Tag) extends Table[TObject](tag, "object") {
    def id = column[String]("id", O.PrimaryKey)
    def blob = column[Array[Byte]]("blob")

    override def * = (id, blob) <> ((TObject.apply _).tupled, TObject.unapply)
  }

  val objects = TableQuery[TObjectTable]

  case class Ref(name: String, value: String)

  case class RefTable(tag: Tag) extends Table[Ref](tag, "ref") {
    def name = column[String]("name", O.PrimaryKey)
    def value = column[String]("value")

    override def * = (name, value) <> ((Ref.apply _).tupled, Ref.unapply)
  }

  val refs = TableQuery[RefTable]
}
