package com.advancedtelematic.treehub.object_store

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import java.util.function.BiFunction

import com.advancedtelematic.data.DataType.ObjectId
import eu.timepit.refined.boolean.Xor
import org.slf4j.LoggerFactory

import scala.util.Try

object FilesystemUsage {

  private val _log = LoggerFactory.getLogger(this.getClass)

  def usageByObject(path: Path): Try[Map[ObjectId, Long]] = {
    import scala.collection.JavaConverters._
    val usage = new java.util.concurrent.ConcurrentHashMap[ObjectId, Long]()

    Try {
      Files.walkFileTree(path, new SimpleFileVisitor[Path]() {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          val size = attrs.size()

          if(size > 0) {
            val dirName = file.getParent.getFileName.toString
            val idXor = ObjectId.parse(s"$dirName${file.getFileName.toString}")

            idXor match {
              case Right(id) =>
                usage.compute(id, new BiFunction[ObjectId, Long, Long]() {
                  override def apply(t: ObjectId, u: Long): Long =
                    Option(u).map(_ + size).getOrElse(size)
                })
              case Left(err) =>
                _log.warn(s"Could not parse file name into commit: $err")
            }

          }

          FileVisitResult.CONTINUE
        }
      })

      usage.asScala.toMap
    }
  }
}
