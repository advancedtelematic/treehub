/**
* Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
* License: MPL-2.0
*/
package com.advancedtelematic.util

import com.typesafe.config.{Config, ConfigFactory}
import org.flywaydb.core.Flyway
import org.scalatest.{BeforeAndAfterAll, Suite}
import slick.driver.MySQLDriver.api._

import scala.collection.JavaConverters._

trait DatabaseSpec extends BeforeAndAfterAll {
  self: Suite =>

  lazy val db = Database.forConfig("", slickDbConfig)

  protected lazy val schemaName = {
    val catalog = testDbConfig.getString("catalog")
    val className = this.getClass.getSimpleName
    val cleanSchemaName = catalog.split(",").head
    cleanSchemaName + "_" + className
  }

  private lazy val config = ConfigFactory.load()

  private lazy val testDbConfig = config.getConfig("database")

  private lazy val slickDbConfig: Config = {
    val withSchemaName =
      ConfigFactory.parseMap(Map("catalog" -> schemaName.toLowerCase).asJava)
    withSchemaName.withFallback(testDbConfig)
  }

  private def resetDatabase() = {
    val url = slickDbConfig.getString("url")
    val user = slickDbConfig.getConfig("properties").getString("user")
    val password = slickDbConfig.getConfig("properties").getString("password")

    val schemaName = slickDbConfig.getString("catalog")

    val flyway = new Flyway
    flyway.setDataSource(url, user, password)
    flyway.setSchemas(schemaName)
    flyway.setLocations("classpath:db.migration")
    flyway.clean()
    flyway.migrate()
  }

  override def beforeAll() {
    resetDatabase()
    super.beforeAll()
  }

  override def afterAll() {
    db.close()
    super.afterAll()
  }
}
