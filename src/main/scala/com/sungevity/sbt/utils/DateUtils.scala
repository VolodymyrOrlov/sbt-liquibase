package com.sungevity.sbt.utils

import org.joda.time.format.DateTimeFormat

import scala.util.Try

object DateUtils {

  implicit class RichDateUtilsString(str: String) {

    private val iso8601Format = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mmZZ");

    def toDatetime = toISO8601Datetime

    def toISO8601Datetime = iso8601Format.parseDateTime(str)

    def isDatetime = isISO8601Datetime

    def isISO8601Datetime = Try {
      toISO8601Datetime
    } map(_ => true) getOrElse(false)
  }

}
