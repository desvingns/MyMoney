package com.kshavrin.mymoney.core.database.converter

import androidx.room.TypeConverter
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class MoneyTypeConverters {

    @TypeConverter
    fun bigDecimalToDouble(value: BigDecimal?): Double? = value?.toDouble()

    @TypeConverter
    fun doubleToBigDecimal(value: Double?): BigDecimal? = value?.let { BigDecimal.valueOf(it) }

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let { LocalDate.ofEpochDay(it) }

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }
}
