/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.DATABASE
import by.enrollie.eversity.data_classes.*
import by.enrollie.eversity.database.usersCache
import by.enrollie.eversity.database.xodus_definitions.XodusPupilProfile
import by.enrollie.eversity.database.xodus_definitions.XodusSchoolsBy
import by.enrollie.eversity.database.xodus_definitions.XodusUser
import by.enrollie.eversity.exceptions.NoSuchUserException
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.query.*

private fun getUserData(userID: UserID) = XodusUser.query(XodusUser::id eq userID).firstOrNull()?.let {
    when (it.type.toEnum()) {
        UserType.Teacher, UserType.Administration -> {
            Teacher(it.id, it.firstName, it.middleName, it.lastName)
        }
        UserType.Pupil -> {
            Pupil(
                it.id,
                it.firstName,
                it.middleName,
                it.lastName,
                (it.profile as XodusPupilProfile).schoolClass.id
            )
        }
        UserType.Parent -> {
            Parent(it.id, it.firstName, it.middleName, it.lastName)
        }
        UserType.SYSTEM, UserType.Social -> {
            object : User {
                override val id: Int = it.id
                override val type: UserType = UserType.SYSTEM
                override val firstName: String = it.firstName
                override val middleName: String? = it.middleName
                override val lastName: String = it.lastName
            }
        }
    }
}

/**
 * Checks, whether user exists
 * @param userID ID of user
 * @return true, if user exists. false otherwise
 */
fun doesUserExist(userID: UserID, store: TransientEntityStore = DATABASE): Boolean {
    if (usersCache.getIfPresent(userID) != null)
        return true
    val user = store.transactional(readonly = true) {
        getUserData(userID)
    }
    if (user != null)
        usersCache.put(userID, user)
    return user != null
}

/**
 * Returns user data
 */
fun getUserInfo(userID: UserID, store: TransientEntityStore = DATABASE): User? {
    val cachedUser = usersCache.getIfPresent(userID)
    if (cachedUser != null)
        return cachedUser
    val user = store.transactional(readonly = true) {
        getUserData(userID)
    }
    if (user != null)
        usersCache.put(userID, user)
    return user
}

fun getAllSchoolsByCredentials(store: TransientEntityStore = DATABASE): List<Pair<Int, Pair<String, String>>> =
    store.transactional(readonly = true) {
        XodusSchoolsBy.all().toList().map {
            Pair(it.user.id, Pair(it.csrfToken, it.sessionID))
        }
    }

/**
 * Saves (or updates existing) credentials for given user ID.
 *
 * @param userID ID of user
 * @param credentials Pair of (CSRFToken, SessionID)
 * @throws NoSuchElementException Thrown, if user does not exist
 */
fun recordSchoolsByCredentials(
    userID: UserID,
    credentials: Pair<String, String>,
    store: TransientEntityStore = DATABASE,
) =
    store.transactional {
        (XodusUser.query(XodusUser::id eq userID).firstOrNull()
            ?: throw NoSuchUserException(userID)).schoolsByCredentials.apply {
            if (this.isEmpty)
                XodusSchoolsBy.new {
                    user = XodusUser.query(XodusUser::id eq userID).first()
                    csrfToken = credentials.first
                    sessionID = credentials.second
                }
            else first().apply {
                csrfToken = credentials.first
                sessionID = credentials.second
            }
        }
    }

/**
 * Returns user's type
 * @param userID User's ID
 * @throws NoSuchElementException Thrown, if user is not registered
 */
fun getUserType(userID: UserID, store: TransientEntityStore = DATABASE): UserType = usersCache.get(userID) {
    store.transactional(readonly = true) {
        getUserData(userID) ?: throw NoSuchUserException(userID)
    }
}.type

/**
 * Returns user's full name
 * @param userID User ID
 */
fun getUserName(userID: UserID, store: TransientEntityStore = DATABASE): UserName =
    store.transactional(readonly = true) {
        (XodusUser.query(XodusUser::id eq userID).firstOrNull() ?: throw NoSuchUserException(userID)).packName()
    }

fun deleteSchoolsByCredentials(userID: UserID, store: TransientEntityStore = DATABASE) = store.transactional {
    XodusSchoolsBy.query(XodusSchoolsBy::user.matches(XodusUser::id eq userID)).firstOrNull()?.delete()
}
