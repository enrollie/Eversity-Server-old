/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.DATABASE
import by.enrollie.eversity.data_classes.Pupil
import by.enrollie.eversity.database.classesCache
import by.enrollie.eversity.database.xodus_definitions.XodusParentProfile
import by.enrollie.eversity.database.xodus_definitions.XodusPupilProfile
import by.enrollie.eversity.database.xodus_definitions.XodusUser
import by.enrollie.eversity.database.xodus_definitions.toPupilsArray
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.query.*

/**
 * Returns pupil's class ID
 *
 * @param userID Pupil's ID
 * @return Class ID
 * @throws IllegalArgumentException Thrown, if user was not found in pupils list
 */
fun getPupilClass(userID: Int, store: TransientEntityStore = DATABASE): Int = store.transactional(readonly = true) {
    XodusPupilProfile.query(XodusPupilProfile::user.matches(XodusUser::id eq userID)).firstOrNull()?.schoolClass?.id
        ?: throw IllegalArgumentException("Pupil with ID $userID does not exist")
}

/**
 * Returns all pupils in given class
 *
 * @param classID Class ID
 * @throws NoSuchElementException Thrown, when class with that ID was not found
 */
fun getPupilsInClass(classID: Int, store: TransientEntityStore = DATABASE) = classesCache.get(classID) {
    store.transactional(readonly = true) {
        getClassInDB(classID)
    }
}.pupils

fun getNonExistentPupilsIDs(pupils: List<Pupil>, store: TransientEntityStore = DATABASE) =
    store.transactional(readonly = true) {
        XodusPupilProfile.query(XodusPupilProfile::user.matches(XodusUser::id inValues pupils.map { it.id })).toList()
            .toPupilsArray().filter { pupil -> !pupils.map { it.id }.contains(pupil.id) }
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
