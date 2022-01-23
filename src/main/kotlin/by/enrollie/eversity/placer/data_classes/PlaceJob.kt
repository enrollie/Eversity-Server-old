/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.placer.data_classes

import by.enrollie.eversity.data_classes.AbsenceNoteWrapper
import by.enrollie.eversity.data_classes.AbsenceReason
import by.enrollie.eversity.data_classes.UserID
import by.enrollie.eversity.serializers.DateSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.joda.time.DateTime

@Serializable
data class PlaceJob(
    @SerialName("pupilId")
    val pupilID: UserID?,
    val lessonsList: List<Short>,
    val reason: AbsenceReason?,
    @Serializable(DateSerializer::class)
    val date: DateTime,
    @SerialName("note")
    val additionalNotes: AbsenceNoteWrapper? = null
)
