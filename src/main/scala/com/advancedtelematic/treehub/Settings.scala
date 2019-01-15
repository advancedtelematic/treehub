package com.advancedtelematic.treehub

import java.nio.file.Paths

import akka.http.scaladsl.model.Uri
import com.advancedtelematic.treehub.object_store.S3Credentials
import com.amazonaws.regions.Regions
import com.typesafe.config.ConfigFactory

trait Settings {
  private lazy val _config = ConfigFactory.load()

  val host = _config.getString("server.host")
  val port = _config.getInt("server.port")
  val coreUri = _config.getString("core.baseUri")
  val packagesApi = _config.getString("core.packagesApi")

  val treeHubUri = {
    val uri = Uri(_config.getString("server.treehubUri"))
    if(!uri.isAbsolute) throw new IllegalArgumentException("Treehub host is not an absolute uri")
    uri
  }

  val localStorePath = Paths.get(_config.getString("treehub.storage.local.path"))

  val deviceRegistryUri = Uri(_config.getString("device_registry.baseUri"))
  val deviceRegistryMyApi = Uri(_config.getString("device_registry.mydeviceUri"))

  lazy val s3Credentials = {
    val accessKey = _config.getString("treehub.storage.s3.accessKey")
    val secretKey = _config.getString("treehub.storage.s3.secretKey")
    val objectBucketId = _config.getString("treehub.storage.s3.bucketId")
    val deltasBucketId = _config.getString("treehub.storage.s3.deltasBucketId")
    val region = Regions.fromName(_config.getString("treehub.storage.s3.region"))

    new S3Credentials(accessKey, secretKey, objectBucketId, deltasBucketId, region)
  }

  lazy val useS3 = _config.getString("treehub.storage.type").equals("s3")

  lazy val staleObjectExpireAfter = _config.getDuration("treehub.storage.staleObjectsExpireAfter")

  lazy val allowRedirectsToS3 = _config.getBoolean("treehub.storage.s3.allowRedirects")
}
