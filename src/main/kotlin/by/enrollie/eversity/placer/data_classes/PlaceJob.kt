/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.placer.data_classes

import by.enrollie.eversity.data_classes.AbsenceNote
import by.enrollie.eversity.data_classes.AbsenceReason
import by.enrollie.eversity.data_classes.Pupil
import by.enrollie.eversity.serializers.DateTimeSerializer
import kotlinx.serialization.Serializable
import org.joda.time.DateTime

@Serializable
data class PlaceJob(
    val pupil: Pupil,
    val absenceList: List<Short>,
    val postedBy: Int?,
    val reason: AbsenceReason?,
    @Serializable(DateTimeSerializer::class)
    val date:DateTime,
    val additionalNotes: AbsenceNote?
)
