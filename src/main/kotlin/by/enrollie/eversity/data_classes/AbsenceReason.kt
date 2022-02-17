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
enum class AbsenceReason { ILLNESS, HEALING, REQUEST, PRINCIPAL_DECISION, UNKNOWN, DUMMY }

fun AbsenceReason.toXodusReason(): XodusAbsenceReason = when (this) {
    AbsenceReason.ILLNESS -> XodusAbsenceReason.ILLNESS
    AbsenceReason.HEALING -> XodusAbsenceReason.HEALING
    AbsenceReason.REQUEST -> XodusAbsenceReason.REQUEST
    AbsenceReason.PRINCIPAL_DECISION -> XodusAbsenceReason.PRINCIPAL_DECISION
    AbsenceReason.UNKNOWN -> XodusAbsenceReason.UNKNOWN
    AbsenceReason.DUMMY -> XodusAbsenceReason.DUMMY
}
