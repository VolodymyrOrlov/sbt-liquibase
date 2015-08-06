package com.sungevity.sbt.utils

object StringUtils {

  implicit class RichStringUtilsString(str: String) {

    def isNumber = str.forall(_.isDigit)

  }



}
