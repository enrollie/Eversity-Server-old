/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity_plugins.plugin_api

import org.joda.time.DateTime

data class AbsenceData(
    val pupilID: Int?,
    val classID: Int,
    val reason: AbsenceReason,
    val date: DateTime,
    val sentBy: Int?,
    val lessonsList: List<Short>
)

interface AbsenceEngineEventListener {
    fun onAdd(absenceData: AbsenceData) = Unit
    fun onRemove(absenceData: AbsenceData) = Unit
}

interface AbsenceEngine {
    /**
     * Registers [AbsenceEngineEventListener] listener and returns it's ID
     */
    fun registerEventListener(listener: AbsenceEngineEventListener): String

    /**
     * Unregisters event listener based on ID
     */
    fun deregisterEventListener(id: String)
}
