package com.advancedtelematic.treehub.object_store

import java.nio.file.{Files, Paths}

import com.advancedtelematic.data.DataType.ObjectId
import com.advancedtelematic.util.TreeHubSpec
import cats.syntax.either._


class FilesystemUsageSpec extends TreeHubSpec {

  test("calculates usage by file") {
    val objId = ObjectId.parse("943ed021ecf10bfe2f62c0e18e075dc6398f5968d05b10b021f3c51f6d583f8c.commit").toOption.get
    val tempDir = Files.createTempDirectory("FilesystemUsageSpec")
    val objDir = Files.createDirectory(Paths.get(tempDir.toString, "94"))
    val tempFile = Files.createFile(Paths.get(objDir.toString, "3ed021ecf10bfe2f62c0e18e075dc6398f5968d05b10b021f3c51f6d583f8c.commit"))
    val text = "hello this is test"

    Files.write(tempFile, text.getBytes)

    val res = FilesystemUsage.usageByObject(tempDir).get

    res.get(objId) should contain(text.length)
  }
}
