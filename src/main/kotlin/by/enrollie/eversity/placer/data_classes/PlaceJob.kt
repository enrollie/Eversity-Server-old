/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.placer.data_classes

import by.enrollie.eversity.data_classes.AbsenceReason
import by.enrollie.eversity.data_classes.Pupil
import kotlinx.serialization.Serializable

@Serializable
data class PlaceJob(
    val pupil: Pupil,
    val absenceList: List<Pair<Short, Boolean>>,
    val reason: AbsenceReason,
    val credentials: Pair<Pair<String, String>, String>,
    val date:String
)
