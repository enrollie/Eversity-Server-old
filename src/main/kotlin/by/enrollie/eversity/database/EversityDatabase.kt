/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

@file:Suppress("DuplicatedCode", "DuplicatedCode", "DuplicatedCode", "DuplicatedCode")

package by.enrollie.eversity.database

import by.enrollie.eversity.data_classes.DayOfWeek
import org.joda.time.DateTime

/**
 * Temporary cache of valid access tokens.
 */
var validTokensSet = mutableSetOf<Triple<Int, String, DateTime>>()

/**
 * Validates given dayMap to contain full timetable
 * @param daysMap Map of days
 * @return True, if timetable is valid. False otherwise
 */
fun validateDaysMap(daysMap: Map<DayOfWeek, Any>): Boolean {
    if (daysMap.size !in 6..7)
        return false
    for (i in 0 until 6) {
        if (!daysMap.containsKey(DayOfWeek.values()[i]))
            return false
    }
    return true
}
