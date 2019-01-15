package com.advancedtelematic.treehub.db

import com.advancedtelematic.data.DataType.ObjectStatus
import com.advancedtelematic.libats.slick.codecs.SlickEnumMapper

object SlickMappings {
  implicit val objectStatusMapping = SlickEnumMapper.enumMapper(ObjectStatus)
}
