/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.data_classes.APIUserType
import by.enrollie.eversity.data_classes.Pupil
import by.enrollie.eversity.database.findCachedPupil
import by.enrollie.eversity.database.tables.Pupils
import by.enrollie.eversity.exceptions.UserNotRegistered
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Returns pupil's class ID
 *
 * @param userID Pupil's ID
 * @return Class ID
 * @throws IllegalArgumentException Thrown, if user is not registered OR is not found in "Pupils" table
 */
fun getPupilClass(userID: Int): Int {
    if (!doesUserExist(userID)) {
        throw UserNotRegistered("User with ID $userID does not exist")
    }
    if (getUserType(userID) != APIUserType.Pupil)
        throw IllegalArgumentException("User with ID $userID is not a pupil (they are ${getUserType(userID)}, in fact)")
    val cachedPupil = findCachedPupil(userID)
    if (cachedPupil != null)
        return cachedPupil.classID
    val classIDElement = transaction {
        Pupils.select {
            Pupils.id eq userID
        }.toList().firstOrNull()
    } ?: throw IllegalArgumentException("Pupil with ID $userID not found in Pupils table.")
    CoroutineScope(Dispatchers.Default).launch{
        getUserName(
            userID,
            APIUserType.Pupil
        )
    } //This call will asynchronously cache pupil
    return classIDElement[Pupils.classID]
}

/**
 * Retrieves pupil's timetable
 * @param userID ID of pupil
 * @throws IllegalArgumentException Thrown, if pupil is not found in database
 */
fun getPupilTimetable(userID: Int) = getClassTimetable(getPupilClass(userID))
fun getClassPupils(classID: Int): List<Pupil> {
    require(doesClassExist(classID)) { "Class with ID $classID does not exist" }
    return transaction {
        Pupils.select { Pupils.classID eq classID }.toList().map {
            Pupil(it[Pupils.id], it[Pupils.firstName], it[Pupils.lastName], it[Pupils.classID])
        }
    }
}