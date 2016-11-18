package com.advancedtelematic.treehub.http

import org.slf4j.LoggerFactory

object Version {

  private val semVerPattern = """^\d+\.\d+\.\d+$"""
  private val log = LoggerFactory.getLogger(getClass)

  /**
    * This function assumes the version string matches the semVerRegex
    */
  private def increment(version: String): Option[String] = {
    val components = version.split("\\.")
    Some(components(0) + "." + components(1) + "." + (Integer.parseInt(components(2)) + 1))
  }

  /**
    * This function assumes both version strings match the semVerRegex
    */
  private def greaterThanOrEqualTo(version1: String, version2: String): Boolean = {
    val components1 = version1.split("\\.")
    val components2 = version2.split("\\.")

    if(components1(0) != components2(0)) {
      components1(0) > components2(0)
    } else if(components1(1) != components2(1)) {
      components1(1) > components2(1)
    } else {
      components1(2) >= components2(2)
    }
  }

  def get(formerVersion: String, newVersion: Option[String]): Option[String] = {
    if(!formerVersion.matches(semVerPattern)) {
      log.error(s"Stored version is invalid: $formerVersion")
      None
    } else {
      newVersion match {
        case None => increment(formerVersion)
        case Some(v) =>
          if(!v.matches(semVerPattern)) {
            log.error(s"New version is invalid: $newVersion")
            None
          } else if (greaterThanOrEqualTo(v, formerVersion)) {
            increment(v)
          } else {
            None
          }
      }
    }
  }

}
