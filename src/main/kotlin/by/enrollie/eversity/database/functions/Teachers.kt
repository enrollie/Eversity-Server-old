/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.DATABASE
import by.enrollie.eversity.data_classes.SchoolClass
import by.enrollie.eversity.data_classes.TwoShiftsTimetable
import by.enrollie.eversity.database.xodus_definitions.XodusTeacherProfile
import by.enrollie.eversity.database.xodus_definitions.XodusUser
import by.enrollie.eversity.database.xodus_definitions.XodusUserType
import by.enrollie.eversity.database.xodus_definitions.toPupilsArray
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.query.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Returns teacher's timetable
 * @param userID Teacher's ID
 * @throws NoSuchElementException Thrown, if no user with given user ID and type was found
 */
fun getTeacherTimetable(userID: Int, store: TransientEntityStore = DATABASE): TwoShiftsTimetable =
    store.transactional(readonly = true) {
        val user =
            XodusUser.query((XodusUser::id eq userID) and ((XodusUser::type eq XodusUserType.TEACHER) or (XodusUser::type eq XodusUserType.ADMINISTRATION)))
                .first()
        return@transactional Json.decodeFromString((user.profile.first() as XodusTeacherProfile).timetable)
    }

/**
 * Returns teacher's class ID if exists
 */
fun getTeacherClass(teacherID: Int, store: TransientEntityStore = DATABASE): SchoolClass? =
    store.transactional(readonly = true) {
        XodusTeacherProfile.query(XodusTeacherProfile::user.matches(XodusUser::id eq teacherID))
            .firstOrNull()?.schoolClass?.let {
                SchoolClass(
                    it.id,
                    it.classTitle,
                    it.isSecondShift,
                    it.classTeacher.user.id,
                    it.pupils.toList().toPupilsArray()
                )
            }
    }
