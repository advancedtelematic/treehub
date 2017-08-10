package com.advancedtelematic.treehub.db

import com.advancedtelematic.libats.data.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.Commit
import slick.jdbc.MySQLProfile.api._

object Schema {
  import com.advancedtelematic.libats.slick.db.SlickAnyVal._
  import com.advancedtelematic.libats.slick.codecs.SlickRefined._
  import com.advancedtelematic.data.DataType._

  class TObjectTable(tag: Tag) extends Table[TObject](tag, "object") {
    def namespace = column[Namespace]("namespace")
    def id = column[ObjectId]("object_id")
    def size = column[Long]("size")

    def pk = primaryKey("pk_object", (namespace, id))

    def uniqueNsId = index("object_unique_namespace", (namespace, id), unique = true)

    override def * = (namespace, id, size) <> ((TObject.apply _).tupled, TObject.unapply)
  }

  val objects = TableQuery[TObjectTable]

  case class RefTable(tag: Tag) extends Table[Ref](tag, "ref") {
    def namespace = column[Namespace]("namespace")
    def name = column[RefName]("name")
    def value = column[Commit]("value")
    def objectId = column[ObjectId]("object_id")
    def published = column[Boolean]("published")

    def pk = primaryKey("pk_ref", (namespace, name))

    def fk = foreignKey("fk_ref_object", objectId, objects)(_.id)

    override def * = (namespace, name, value, objectId) <> ((Ref.apply _).tupled, Ref.unapply)
  }

  protected[db] val refs = TableQuery[RefTable]
}
