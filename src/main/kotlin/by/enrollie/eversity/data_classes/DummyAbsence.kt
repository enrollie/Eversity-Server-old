/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import org.joda.time.DateTime
import org.joda.time.LocalTime

/**
 * Used to represent dummy absence **inside** application
 */
data class DummyAbsence(
    val classID: ClassID, private var dateTime_: DateTime,
) {
    val dateTime: DateTime

    init {
        dateTime = dateTime_.withTime(LocalTime.MIDNIGHT)
    }
}
