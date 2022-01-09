/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.DATABASE
import by.enrollie.eversity.database.validTokensSet
import by.enrollie.eversity.database.xodus_definitions.XodusToken
import by.enrollie.eversity.database.xodus_definitions.XodusUser
import by.enrollie.eversity.tokenCacheValidityMinutes
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.query.*
import org.joda.time.DateTime
import org.joda.time.Minutes
import java.util.*

/**
 * Issues new token for given user ID and saving it to database
 *
 * @param userID User ID to issue token
 * @return [String], containing issued token
 */
fun issueToken(userID: Int, store: TransientEntityStore = DATABASE): String = store.transactional {
    val hashNumber = it.getSequence("tokens").increment()
    XodusToken.new {
        token = UUID.nameUUIDFromBytes((hashNumber.toString() + DateTime.now().toString()).toByteArray()).toString()
        issueDate = DateTime.now()
        user = XodusUser.query(XodusUser::id eq userID).first()
        invalid = false
    }.token
}

/**
 * Invalidates all user tokens
 *
 * @param userID User ID to invalidate tokens
 * @throws IllegalArgumentException Thrown, if no user with such ID is registered
 */
fun invalidateAllTokens(userID: Int, store: TransientEntityStore = DATABASE) = store.transactional {
    XodusToken.query(XodusToken::user.matches(XodusUser::id eq userID)).toList().forEach {
        it.invalid = true
    }
}

/**
 * Finds access token in databases.
 *
 * @param userID ID of user
 * @param token Token to check
 * @return If token is found and it is not banned, returns (true, null). If token is found, but it is banned, returns (false, reason of ban). If token is not found, returns (false,null).
 */
fun checkToken(userID: Int, token: String, store: TransientEntityStore = DATABASE): Boolean {
    if (validTokensSet.find {
            it.first == userID && it.second == token && Minutes.minutesBetween(
                it.third,
                DateTime.now()
            ).minutes <= tokenCacheValidityMinutes
        } != null) {
        return true
    }
    return store.transactional(readonly = true) {
        val foundToken =
            XodusToken.query((XodusToken::user.matches(XodusUser::id eq userID)) and (XodusToken::token eq token))
                .firstOrNull()
        if (foundToken != null) {
            if (!foundToken.invalid) {
                validTokensSet.add(Triple(userID, token, DateTime.now()))
                return@transactional true
            } else return@transactional false
        } else false
    }
}

/**
 * Invalidates given user token
 * @param userID User ID
 * @param token Token to invalidate
 */
fun invalidateSingleToken(userID: Int, token: String, store: TransientEntityStore = DATABASE) = store.transactional {
    XodusToken.query((XodusToken::user.matches(XodusUser::id eq userID)) and (XodusToken::token eq token)).firstOrNull()?.apply {
        invalid = true
    }
}
