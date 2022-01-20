/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.DATABASE
import by.enrollie.eversity.data_classes.UserID
import by.enrollie.eversity.database.tokensCache
import by.enrollie.eversity.database.xodus_definitions.XodusToken
import by.enrollie.eversity.database.xodus_definitions.XodusUser
import by.enrollie.eversity.security.EversityJWT
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.query.*
import org.joda.time.DateTime
import java.util.*

/**
 * Issues new token for given user ID and saving it to database
 *
 * @param userID User ID to issue token
 * @return [String], containing issued token
 */
fun issueToken(userID: UserID, store: TransientEntityStore = DATABASE): String = store.transactional {
    val hashNumber = it.getSequence("tokens").increment()
    val newToken = XodusToken.new {
        token = UUID.nameUUIDFromBytes((hashNumber.toString() + DateTime.now().toString()).toByteArray()).toString()
        issueDate = DateTime.now()
        user = XodusUser.query(XodusUser::id eq userID).first()
        invalid = false
    }.token
    EversityJWT.instance.logger.debug("Issued new token for user $userID: $newToken")
    tokensCache.put(Pair(userID, newToken), true)
    newToken
}

/**
 * Invalidates all user tokens
 *
 * @param userID User ID to invalidate tokens
 * @throws IllegalArgumentException Thrown, if no user with such ID is registered
 */
fun invalidateAllTokens(userID: UserID, store: TransientEntityStore = DATABASE) = store.transactional {
    EversityJWT.instance.logger.debug("Invalidating all tokens for user $userID")
    XodusToken.query(XodusToken::user.matches(XodusUser::id eq userID)).toList().forEach {
        tokensCache.invalidate(Pair(userID, it.token))
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
fun checkToken(userID: UserID, token: String, store: TransientEntityStore = DATABASE): Boolean =
    tokensCache.get(Pair(userID, token)) {
        store.transactional(readonly = true) {
            val foundToken =
                XodusToken.query((XodusToken::user.matches(XodusUser::id eq userID)) and (XodusToken::token eq token))
                    .firstOrNull()
            if (foundToken != null) {
                EversityJWT.instance.logger.debug("Authenticated user with user ID $userID and token $token")
                return@transactional !foundToken.invalid
            } else {
                EversityJWT.instance.logger.debug("Rejected authentication to user with ID $userID and token $token")
                return@transactional false
            }
        }
    }

/**
 * Invalidates given user token
 * @param userID User ID
 * @param token Token to invalidate
 */
fun invalidateSingleToken(userID: UserID, token: String, store: TransientEntityStore = DATABASE) = store.transactional {
    EversityJWT.instance.logger.debug("Invalidating token $token for user ID $userID")
    tokensCache.invalidate(Pair(userID, token))
    XodusToken.query((XodusToken::user.matches(XodusUser::id eq userID)) and (XodusToken::token eq token)).firstOrNull()
        ?.apply {
            invalid = true
        }
}

/**
 * Returns all user's tokens
 */
fun getUserTokensCount(userID: UserID, store: TransientEntityStore = DATABASE) = store.transactional(readonly = true) {
    XodusToken.query(XodusToken::user.matches(XodusUser::id eq userID)).size()
}
