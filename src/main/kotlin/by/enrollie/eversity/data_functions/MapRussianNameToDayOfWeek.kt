/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_functions

import by.enrollie.eversity.data_classes.DayOfWeek
import java.util.*

/**
 * Converts russian week day (i.e. Понедельник) to corresponding [DayOfWeek]
 * @param string Russian week day name
 * @return Corresponding [DayOfWeek]
 */
fun russianDayNameToDayOfWeek(string: String) = when(string.lowercase(Locale.getDefault())){
    "понедельник" -> DayOfWeek.MONDAY
    "вторник" -> DayOfWeek.TUESDAY
    "среда" -> DayOfWeek.WEDNESDAY
    "четверг" -> DayOfWeek.THURSDAY
    "пятница" -> DayOfWeek.FRIDAY
    "суббота" -> DayOfWeek.SATURDAY
    "воскресенье" -> DayOfWeek.SUNDAY
    else -> throw IllegalArgumentException("Value $string is not a valid day of week name.")
}
