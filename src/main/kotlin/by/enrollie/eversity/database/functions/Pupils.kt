/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.DATABASE
import by.enrollie.eversity.data_classes.Pupil
import by.enrollie.eversity.data_classes.SchoolClass
import by.enrollie.eversity.database.classesCache
import by.enrollie.eversity.database.xodus_definitions.*
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.query.*
import org.joda.time.DateTime
import org.joda.time.LocalTime

/**
 * Returns pupil's class ID
 *
 * @param userID Pupil's ID
 * @return Class ID
 * @throws IllegalArgumentException Thrown, if user was not found in pupils list
 */
fun getPupilClass(userID: Int, store: TransientEntityStore = DATABASE): SchoolClass =
    store.transactional(readonly = true) {
        XodusPupilProfile.query(XodusPupilProfile::user.matches(XodusUser::id eq userID))
            .firstOrNull()?.schoolClass?.toSchoolClass()
            ?: throw IllegalArgumentException("Pupil with ID $userID does not exist")
    }

/**
 * Returns all pupils in given class
 *
 * @param date Date for which to get pupils list (used to get pupils list with pupils that left school on specific date)
 * @param classID Class ID
 * @throws NoSuchElementException Thrown, when class with that ID was not found
 */
fun getPupilsInClass(date: DateTime, classID: Int, store: TransientEntityStore = DATABASE) =
    store.transactional(readonly = true) {
        XodusClass.query((XodusClass::id eq classID)).first().let {
            classesCache.put(classID, SchoolClass(it.id, it.classTitle, it.isSecondShift, it.classTeacher.user.id))
            it.pupils.filter {
                (it.user.disableDate eq null) or (it.user.disableDate ge date.withTime(LocalTime.MIDNIGHT))
            }.toList().toPupilsArray()
        }
    }

fun getNonExistentPupilsIDs(pupils: List<Pupil>, store: TransientEntityStore = DATABASE) =
    store.transactional(readonly = true) {
        XodusPupilProfile.query(XodusPupilProfile::user.matches(XodusUser::id inValues pupils.map { it.id })).toList()
            .toPupilsArray().map { it.id }.let { idList ->
                pupils.filter { it.id !in idList }
            }
    }

/**
 * Assigns pupils profiles to parents
 * @param assignList List of (Pupil ID, Parent ID) pairs
 */
fun assignPupilsToParents(assignList: List<Pair<Int, Int>>, store: TransientEntityStore = DATABASE) =
    store.transactional {
        for (pair in assignList) {
            XodusParentProfile.query(XodusParentProfile::user.matches(XodusUser::id eq pair.second))
                .firstOrNull()?.pupils?.add(
                    XodusPupilProfile.query(XodusPupilProfile::user.matches(XodusUser::id eq pair.first)).first()
                )
        }
    }

fun getPupilsCount(day: DateTime, store: TransientEntityStore = DATABASE) = store.transactional(readonly = true) {
    XodusPupilProfile.filter {
        (it.user.disableDate eq null) or (it.user.disableDate ge day)
    }
        .toList().groupingBy {
            println(it.user.packName().toString() + it.user.disableDate.toString())
            it.schoolClass.isSecondShift
        }.eachCount().let {
            Pair(it[false] ?: 0, it[true] ?: 0)
        }
}
