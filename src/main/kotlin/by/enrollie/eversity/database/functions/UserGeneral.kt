/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.DATABASE
import by.enrollie.eversity.data_classes.UserName
import by.enrollie.eversity.data_classes.UserType
import by.enrollie.eversity.database.xodus_definitions.XodusSchoolsBy
import by.enrollie.eversity.database.xodus_definitions.XodusUser
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.query.*

/**
 * Checks, whether user exists
 * @param userID ID of user
 * @return true, if user exists. false otherwise
 */
fun doesUserExist(userID: Int, store: TransientEntityStore = DATABASE): Boolean =
    store.transactional(readonly = true) { XodusUser.query(XodusUser::id eq userID).any() }

/**
 * Obtains user credentials from database
 *
 * @param userID ID of user to get credentials
 * @return Triple, made of (csrftoken?, sessionid?, APIToken)
 * @throws IllegalArgumentException Thrown, if user is not found
 * @throws NoSuchElementException Thrown, if no credentials were found OR they are outdated (in any of cases, you are required to re-register credentials)
 * @throws IllegalStateException Thrown, if more than two credentials sets have been found
 */
fun obtainSchoolsByCredentials(userID: Int, store: TransientEntityStore = DATABASE): Pair<String, String> =
    store.transactional(readonly = true) {
        val credentials = XodusUser.query(XodusUser::id eq userID).first().schoolsByCredentials.first()
        return@transactional Pair(credentials.csrfToken, credentials.sessionID)
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
fun recordSchoolsByCredentials(userID: Int, credentials: Pair<String, String>, store: TransientEntityStore = DATABASE) =
    store.transactional {
        XodusUser.query(XodusUser::id eq userID).first().schoolsByCredentials.apply {
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
fun getUserType(userID: Int, store: TransientEntityStore = DATABASE): UserType = store.transactional(readonly = true) {
    XodusUser.query(XodusUser::id eq userID).first().type.toEnum()
}

/**
 * Returns user's full name
 * @param userID User ID
 */
fun getUserName(userID: Int, store: TransientEntityStore = DATABASE): UserName = store.transactional(readonly = true) {
    XodusUser.query(XodusUser::id eq userID).first().packName()
}

fun removeUser(userID: Int, store: TransientEntityStore = DATABASE) = store.transactional {
    XodusUser.query(XodusUser::id eq userID).first().delete()
}

fun deleteSchoolsByCredentials(userID: Int, store: TransientEntityStore = DATABASE) = store.transactional {
    XodusSchoolsBy.query(XodusSchoolsBy::user.matches(XodusUser::id eq userID)).firstOrNull()?.delete()
}
