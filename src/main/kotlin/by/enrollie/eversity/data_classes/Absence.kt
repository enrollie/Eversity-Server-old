/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import by.enrollie.eversity.serializers.DateSerializer
import by.enrollie.eversity.serializers.DateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.joda.time.DateTime

@Serializable
data class Absence(
    @SerialName("pupilId")
    val pupilID: Int?,
    @SerialName("classId")
    val classID: Int,
    @SerialName("sentBy")
    val sentByID: Int?,
    @Serializable(DateSerializer::class)
    val date: DateTime,
    val reason: AbsenceReason,
    val lessonsList: List<Short>,
    @Serializable(DateTimeSerializer::class)
    @SerialName("lastEditTimestamp")
    val lastEdit: DateTime,
)
