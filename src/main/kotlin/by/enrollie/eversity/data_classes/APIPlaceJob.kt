/*
 * Copyright (c) 2021.
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
    val absenceList: List<Pair<Short, Boolean>>,
    val reason: AbsenceReason,
    val date: String
)