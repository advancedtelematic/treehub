package com.advancedtelematic.treehub.object_store

import java.nio.file.Files

import com.advancedtelematic.util.TreeHubSpec

import scala.collection.JavaConverters._


class FilesystemUsageSpec extends TreeHubSpec {

  test("can calculate a directory usage") {
    val tempDir = Files.createTempDirectory("FilesystemUsageSpec")
    val tempFile = Files.createTempFile(tempDir, "FilesystemUsageSpecFile", ".txt")

    Files.write(tempFile, List("some text").asJava)

    FilesystemUsage.usage(tempDir).get should be > 0l
  }
}
