/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import kotlinx.serialization.Serializable

@Serializable
data class TimetableDay(val dayOfWeek: DayOfWeek, val lessonsArray: Array<Lesson>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TimetableDay

        if (dayOfWeek != other.dayOfWeek) return false
        if (!lessonsArray.contentEquals(other.lessonsArray)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dayOfWeek.hashCode()
        result = 31 * result + lessonsArray.contentHashCode()
        return result
    }
}
