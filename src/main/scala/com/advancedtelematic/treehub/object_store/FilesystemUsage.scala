package com.advancedtelematic.treehub.object_store

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import java.util.concurrent.atomic.AtomicLong
import java.util.function.BiFunction

import com.advancedtelematic.data.DataType.ObjectId

import scala.util.Try

object FilesystemUsage {

  def usageByObject(path: Path): Try[Map[ObjectId, Long]] = {
    import scala.collection.JavaConverters._
    val usage = new java.util.concurrent.ConcurrentHashMap[ObjectId, Long]()

    Try {
      Files.walkFileTree(path, new SimpleFileVisitor[Path]() {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          val size = attrs.size()

          if(size > 0) {
            val dirName = file.getParent.getFileName.toString
            val id = ObjectId(s"$dirName${file.getFileName.toString}")

            usage.compute(id, new BiFunction[ObjectId, Long, Long]() {
              override def apply(t: ObjectId, u: Long): Long =
                Option(u).map(_ + size).getOrElse(size)
            })
          }

          FileVisitResult.CONTINUE
        }
      })

      usage.asScala.toMap
    }
  }
}
