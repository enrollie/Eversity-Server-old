/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.data_classes.APIUserType
import by.enrollie.eversity.data_classes.Pupil
import by.enrollie.eversity.data_classes.Teacher
import by.enrollie.eversity.data_classes.User
import by.enrollie.eversity.database.*
import by.enrollie.eversity.database.tables.Credentials
import by.enrollie.eversity.database.tables.Pupils
import by.enrollie.eversity.database.tables.Teachers
import by.enrollie.eversity.database.tables.Users
import by.enrollie.eversity.exceptions.UserNotRegistered
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Checks, whether user exists
 * @param userID ID of user
 * @return true, if user exists. false otherwise
 */
fun doesUserExist(userID: Int): Boolean {
    if (findCachedUser(userID) != null) {
        return true
    }
    val tos = transaction {
        Users.select { Users.id eq userID }.toList()
    }
    if (tos.isNotEmpty())
        CoroutineScope(Dispatchers.Default).launch { cacheUser(User(userID, getUserType(userID))) }
    return tos.isNotEmpty()
}

/**
 * Obtains user credentials from database
 *
 * @param userID ID of user to get credentials
 * @return Triple, made of (csrftoken?, sessionid?, APIToken)
 * @throws IllegalArgumentException Thrown, if user is not found
 * @throws NoSuchElementException Thrown, if no credentials were found OR they are outdated (in any of cases, you are required to re-register credentials)
 * @throws IllegalStateException Thrown, if more than two credentials sets have been found
 */
fun obtainCredentials(userID: Int): Triple<String?, String?, String> {
    if (!doesUserExist(userID)) {
        throw UserNotRegistered("User with ID $userID does not exist")
    }
    val cachedCredentials = findCachedCredentials(userID)
    if (cachedCredentials != null) {
        return Triple(cachedCredentials.first.first, cachedCredentials.first.second, cachedCredentials.second)
    }
    val credentialsList = transaction {
        Credentials.select {
            Credentials.id eq userID
        }.toList()
    }
    if (credentialsList.isEmpty()) {
        throw NoSuchElementException("Credentials list for user ID $userID is empty")
    }
    val credentials =
        credentialsList.firstOrNull() ?: throw NoSuchElementException("Credentials first element is null")
    cacheCredentials(
        userID, Pair(
            Pair(
                credentials[Credentials.csrfToken],
                credentials[Credentials.sessionID]
            ), credentials[Credentials.token]
        )
    )
    return Triple(
        credentials[Credentials.csrfToken],
        credentials[Credentials.sessionID],
        credentials[Credentials.token]
    )
}

/**
 * Saves (or updates existing) credentials for given user ID.
 *
 * @param userID ID of user
 * @param credentials Triple, containing (csrftoken?, sessionid?, APItoken)
 * @throws IllegalArgumentException Thrown, if user does not exist
 */
fun insertOrUpdateCredentials(userID: Int, credentials: Triple<String?, String?, String>) {
    if (!doesUserExist(userID)) {
        throw UserNotRegistered("User with ID $userID does not exist")
    }
    transaction {
        Credentials.deleteWhere {
            Credentials.id eq userID
        }
        Credentials.insert {
            it[id] = userID
            it[csrfToken] = credentials.first
            it[sessionID] = credentials.second
            it[token] = credentials.third
        }
    }
    cacheCredentials(userID, Pair(Pair(credentials.first, credentials.second), credentials.third))
}

/**
 * Returns user's type
 * @param userID User's ID
 * @throws UserNotRegistered Thrown, if user is not registered
 */
fun getUserType(userID: Int): APIUserType {
    val cachedUser = findCachedUser(userID)
    if (cachedUser != null)
        return cachedUser.type
    val type = transaction {
        Users.select {
            Users.id eq userID
        }.toList().firstOrNull()
    } ?: throw UserNotRegistered("User with ID $userID not found in database.")
    cacheUser(User(userID, APIUserType.valueOf(type[Users.type])))
    return APIUserType.valueOf(type[Users.type])
}

/**
 * Returns user's full name
 * @param userID User ID
 * @param type User type (if you don't have one, see [getUserType]
 * @return Triple, made of First name, Middle name and Last name correspondingly
 */
fun getUserName(userID: Int, type: APIUserType): Triple<String, String?, String> {
    if (userID == -1)
        return Triple("Dummy", "Dummy", "Dummy")
    if (!doesUserExist(userID)) {
        throw UserNotRegistered("User with ID $userID does not exist")
    }
    return when (type) {
        APIUserType.Parent -> {
            Triple("WE DON\'T STORE PARENTS DATA YET", null, "why did you even ask lol")
        }
        APIUserType.Pupil -> {
            val cachedPupil = findCachedPupil(userID)
            if (cachedPupil != null)
                return Triple(cachedPupil.firstName, null, cachedPupil.lastName)
            val pupil = transaction {
                Pupils.select {
                    Pupils.id eq userID
                }.toList().firstOrNull()
            }
                ?: throw NoSuchElementException("Pupil with ID $userID was not found in Pupils database. Are you sure that user with ID $userID is a pupil?")
            cachePupil(
                Pupil(
                    userID,
                    pupil[Pupils.firstName],
                    pupil[Pupils.lastName],
                    pupil[Pupils.classID]
                )
            )
            Triple(pupil[Pupils.firstName], null, pupil[Pupils.lastName])
        }
        APIUserType.Teacher -> {
            val cachedTeacher = findCachedTeacher(userID)
            if (cachedTeacher != null)
                return Triple(
                    cachedTeacher.first.firstName,
                    cachedTeacher.first.middleName,
                    cachedTeacher.first.lastName
                )
            val teacher = transaction {
                Teachers.select {
                    Teachers.id eq userID
                }.toList().firstOrNull()
            }
                ?: throw NoSuchElementException("Teacher with ID $userID was not found in database. Are you sure that user $userID is a teacher?")
            cacheTeacher(
                Teacher(
                    userID,
                    teacher[Teachers.firstName],
                    teacher[Teachers.middleName],
                    teacher[Teachers.lastName]
                ), teacher[Teachers.classID]
            )
            Triple(teacher[Teachers.firstName], teacher[Teachers.middleName], teacher[Teachers.lastName])
        }
        APIUserType.SYSTEM -> {
            //TODO: Record tech guys info somewhere
            Triple("Eversity", "System", "Administrator")
        }
        APIUserType.Social -> {
            val socialTeacher = transaction {
                Teachers.select {
                    Teachers.id eq userID
                }.toList().firstOrNull()
            }
                ?: throw NoSuchElementException("Social teacher with ID $userID was not found in database. Are you sure that user $userID is a social teacher?")
            return Triple(
                socialTeacher[Teachers.firstName],
                socialTeacher[Teachers.middleName],
                socialTeacher[Teachers.lastName]
            )
        }
        else -> {
            Triple("", "", "")
        }
    }
}

fun getAllCredentials(): List<Pair<Int, Triple<String?, String?, String>>> {
    return transaction {
        Credentials.selectAll().toList().map {
            Pair(
                it[Credentials.id],
                Triple(it[Credentials.csrfToken], it[Credentials.sessionID], it[Credentials.token])
            )
        }
    }
}