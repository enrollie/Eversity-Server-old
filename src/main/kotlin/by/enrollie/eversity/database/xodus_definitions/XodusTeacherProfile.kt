/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.xodus_definitions

import by.enrollie.eversity.data_classes.Teacher
import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.XdNaturalEntityType
import kotlinx.dnq.link.OnDeletePolicy
import kotlinx.dnq.xdLink0_1
import kotlinx.dnq.xdRequiredStringProp

open class XodusTeacherProfile(entity: Entity) : XodusBaseUserProfile(entity) {
    companion object : XdNaturalEntityType<XodusTeacherProfile>() {
        override fun new(init: XodusTeacherProfile.() -> Unit): XodusTeacherProfile {
            return super.new(init).apply {
                type = XodusUserType.TEACHER
            }
        }
    }

    var schoolClass by xdLink0_1(XodusClass, onTargetDelete = OnDeletePolicy.CLEAR)

    /**
     * Use Json.decodeFromString() with TwoShiftsTimetable type to get timetable
     */
    var timetable by xdRequiredStringProp { }

    protected fun toTeacher(): Teacher = Teacher(user.id, user.firstName, user.middleName, user.lastName)
}
