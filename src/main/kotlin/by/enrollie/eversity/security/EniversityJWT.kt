/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich as part of Enrollie team
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm

/**
 *
 */
open class EversityJWT private constructor(secret: String) {
    private val algorithm = Algorithm.HMAC256(secret)
    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .build()

    /**
     * Generate JWT with userID and Token
     *
     * @param userID ID of user to issue JWT
     * @param token User access token
     *
     * @return String, made of JWT
     */
    fun sign(userID: String, token: String): String = JWT
        .create()
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .withClaim(ClAIM_USERID, userID)
        .withClaim(CLAIM_TOKEN, token)
        .sign(algorithm)

    companion object {
        lateinit var instance: EversityJWT
            private set

        fun initialize(secret: String) {
            synchronized(this) {
                if (!this::instance.isInitialized) {
                    instance = EversityJWT(secret)
                }
            }
        }

        private const val ISSUER = "Enrollie-EversityJWT"
        private const val AUDIENCE = "Enrollie/EversityJWT"
        const val ClAIM_USERID = "userID"
        const val CLAIM_TOKEN = "token"
    }
}