/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import kotlinx.serialization.Serializable

@Serializable
data class APIPlaceJob(
    val pupilID: Int,
    val classID: Int,
    val absenceList: List<Short>,
    val reason: AbsenceReason?,
    val date: String,
    val additionalNotes: AbsenceNote? = null
)
