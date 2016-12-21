package com.advancedtelematic.treehub.object_store

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import java.util.concurrent.atomic.AtomicLong

import scala.util.Try

object FilesystemUsage {

  def usage(path: Path): Try[Long] = {
    val usage = new AtomicLong(0)

    Try {
      Files.walkFileTree(path, new SimpleFileVisitor[Path]() {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          usage.addAndGet(attrs.size())
          FileVisitResult.CONTINUE
        }
      })

      usage.get()
    }
  }
}
