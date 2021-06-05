/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_functions

import by.enrollie.eversity.data_classes.TimeConstraints

/**
 * Converts string to [TimeConstraints]. Requires data to be in "hh:mm - hh:mm" format.
 *
 * @return TimeConstraints or null, if string does not contain valid TimeConstraints
 */
fun String.toTimeConstraint(): TimeConstraints? {
    val startTime = this.substringBefore(" – ")
    if (startTime == this) {
        return null
    }
    return try{
        val endTime = this.substringAfter(" – ")
        val startHour = startTime.substringBefore(':').toShort()
        val startMinute = startTime.substringAfter(':').toShort()

        val endHour = endTime.substringBefore(':').toShort()
        val endMinute = endTime.substringAfter(':').toShort()
        TimeConstraints(startHour, startMinute, endHour, endMinute)
    } catch (e:NumberFormatException){
        null
    }
}