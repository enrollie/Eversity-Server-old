/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.xodus_definitions

import by.enrollie.eversity.data_classes.AbsenceReason
import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.*
import kotlinx.dnq.link.OnDeletePolicy

class XodusAbsenceReason(entity: Entity) : XdEnumEntity(entity) {
    companion object : XdEnumEntityType<XodusAbsenceReason>() {
        val ILLNESS by enumField { title = AbsenceReason.ILLNESS.name }
        val HEALING by enumField { title = AbsenceReason.HEALING.name }
        val REQUEST by enumField { title = AbsenceReason.REQUEST.name }
        val PRINCIPAL_DECISION by enumField { title = AbsenceReason.PRINCIPAL_DECISION.name }
        val UNKNOWN by enumField { title = AbsenceReason.UNKNOWN.name }
        val DUMMY by enumField { title = AbsenceReason.DUMMY.name }
        fun XodusAbsenceReason.toAbsenceReason(): AbsenceReason = AbsenceReason.valueOf(this.title)
    }

    var title by xdRequiredStringProp(unique = true)
}

class XodusAbsence(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XodusAbsence>()

    var date by xdRequiredDateTimeProp { }
    var schoolClass by xdLink1(XodusClass, onTargetDelete = OnDeletePolicy.CASCADE)
    var pupil by xdLink0_1(XodusPupilProfile)
    var lessons by xdSetProp<XodusAbsence, Short>()
    var reason by xdLink1(XodusAbsenceReason)
    var sentBy by xdLink0_1(XodusUser)
    var lastChangeDate by xdRequiredDateTimeProp { }
}
