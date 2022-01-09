/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.xodus_definitions

import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.*
import kotlinx.dnq.link.OnDeletePolicy
import kotlinx.dnq.simple.min

class XodusClass(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XodusClass>()

    var id by xdRequiredIntProp { min(0) }
    var classTitle by xdRequiredStringProp { }
    var isSecondShift by xdBooleanProp { }
    var classTeacher by xdLink1(XodusTeacherProfile)
    var timetable by xdLink1(
        XodusClassTimetable,
        onDelete = OnDeletePolicy.CASCADE,
        onTargetDelete = OnDeletePolicy.CLEAR
    )
    val pupils by xdLink0_N(XodusPupilProfile)
}
