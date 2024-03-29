/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import by.enrollie.eversity.serializers.TimeConstraintsSerializer
import kotlinx.serialization.Serializable

@Serializable(with = TimeConstraintsSerializer::class)
data class TimeConstraints(val startHour: Short, val startMinute: Short, val endHour: Short, val endMinute: Short)

fun com.neitex.TimeConstraints.toTimeConstraints(): TimeConstraints =
    TimeConstraints(startHour, startMinute, endHour, endMinute)
