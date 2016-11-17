package com.advancedtelematic.treehub.db

import org.genivi.sota.data.Namespace
import slick.driver.MySQLDriver.api._

object Schema {
  import SlickAnyVal._
  import org.genivi.sota.refined.SlickRefined._
  import com.advancedtelematic.data.DataType._

  class TObjectTable(tag: Tag) extends Table[TObject](tag, "object") {
    def namespace = column[Namespace]("namespace")
    def id = column[ObjectId]("object_id")
    def blob = column[Array[Byte]]("blob")

    def pk = primaryKey("pk_object", (namespace, id))

    def uniqueNsId = index("object_unique_namespace", (namespace, id), unique = true)

    override def * = (namespace, id, blob) <> ((TObject.apply _).tupled, TObject.unapply)
  }

  val objects = TableQuery[TObjectTable]

  case class RefTable(tag: Tag) extends Table[Ref](tag, "ref") {
    def namespace = column[Namespace]("namespace")
    def name = column[RefName]("name")
    def value = column[Commit]("value")
    def objectId = column[ObjectId]("object_id")
    def savedInCore = column[Boolean]("saved_in_core")
    def version = column[String]("version")

    def pk = primaryKey("pk_ref", (namespace, name))

    def fk = foreignKey("fk_ref_object", objectId, objects)(_.id)

    override def * = (namespace, name, value, objectId, savedInCore, version) <> ((Ref.apply _).tupled, Ref.unapply)
  }

  protected[db] val refs = TableQuery[RefTable]
}
