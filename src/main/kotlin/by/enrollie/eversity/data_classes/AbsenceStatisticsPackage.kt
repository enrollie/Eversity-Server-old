/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import kotlinx.serialization.Serializable

@Serializable
data class AbsenceStatisticsPackage(
    val illness: Int,
    val healing: Int,
    val request: Int,
    val competition: Int,
    val unknown: Int,
) {
    companion object {
        fun fromMap(map: Map<AbsenceReason, Int>): AbsenceStatisticsPackage =
            AbsenceStatisticsPackage(map[AbsenceReason.ILLNESS]!!,
                map[AbsenceReason.HEALING]!!,
                map[AbsenceReason.REQUEST]!!,
                map[AbsenceReason.COMPETITION]!!,
                map[AbsenceReason.UNKNOWN]!!)
    }

    operator fun get(reasonType: AbsenceReason): Int =
        when (reasonType) {
            AbsenceReason.ILLNESS -> illness
            AbsenceReason.HEALING -> healing
            AbsenceReason.REQUEST -> request
            AbsenceReason.COMPETITION -> competition
            AbsenceReason.UNKNOWN -> unknown
            else -> 0
        }
}
