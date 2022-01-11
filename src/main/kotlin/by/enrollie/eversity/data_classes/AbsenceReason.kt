/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import by.enrollie.eversity.database.xodus_definitions.XodusAbsenceReason
import kotlinx.serialization.Serializable

@Serializable
enum class AbsenceReason { ILLNESS, HEALING, REQUEST, COMPETITION, UNKNOWN, DUMMY }

fun AbsenceReason.toXodusReason(): XodusAbsenceReason = when (this) {
    AbsenceReason.ILLNESS -> XodusAbsenceReason.ILLNESS
    AbsenceReason.HEALING -> XodusAbsenceReason.HEALING
    AbsenceReason.REQUEST -> XodusAbsenceReason.REQUEST
    AbsenceReason.COMPETITION -> XodusAbsenceReason.COMPETITION
    AbsenceReason.UNKNOWN -> XodusAbsenceReason.UNKNOWN
    AbsenceReason.DUMMY -> XodusAbsenceReason.DUMMY
}
