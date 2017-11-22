package hbase.util

import com.github.nscala_time.time.Imports.{ DateTimeFormat, YearMonth }

import model.AdminData

/**
 * RowKeyUtils
 * ----------------
 * Author: haqa
 * Date: 03 November 2017 - 09:10
 * Copyright (c) 2017  Office for National Statistics
 */
object RowKeyUtils {

  final val REFERENCE_PERIOD_FORMAT = "yyyyMM"
  final val DELIMITER = "~"

  def createRowKey(referencePeriod: YearMonth, id: String): String =
    String.join(DELIMITER, referencePeriod.toString(REFERENCE_PERIOD_FORMAT), id)

  def createAdminDataFromRowKey(rowKey: String): AdminData = {
    val compositeRowKeyParts: Array[String] = rowKey.split(DELIMITER)
    val referencePeriod: YearMonth =
      YearMonth.parse(compositeRowKeyParts.head, DateTimeFormat.forPattern(REFERENCE_PERIOD_FORMAT))
    val id = compositeRowKeyParts.last
    AdminData(referencePeriod, id)
  }

}