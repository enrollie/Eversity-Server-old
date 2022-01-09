/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.plugins

import by.enrollie.eversity.database.functions.checkToken
import by.enrollie.eversity.database.functions.getUserType
import by.enrollie.eversity.security.EversityJWT
import by.enrollie.eversity.security.User
import com.auth0.jwt.exceptions.JWTVerificationException
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*

fun Application.configureAuthentication() {
    install(Authentication) {
        jwt("jwt") {
            verifier(EversityJWT.instance.verifier)
            realm = "Eversity-Core/JWT"
            validate { jwtCredential ->
                val userID = jwtCredential.payload.getClaim("userID").asString().toIntOrNull()
                val token = jwtCredential.payload.getClaim("token").asString()
                if (userID != null && checkToken(userID, token)) {
                    EversityJWT.instance.logger.debug("Authenticated user with user ID $userID and token $token")
                    return@validate User(userID, getUserType(userID), token)
                } else {
                    EversityJWT.instance.logger.debug("Rejected authentication to user with ID $userID and token $token")
                    return@validate null
                }
            }
        }
    }
}

fun authenticateWithJWT(jwtToken: String): User? {
    val jwt = try {
        EversityJWT.instance.verifier.verify(jwtToken)
    } catch (e: JWTVerificationException) {

        null
    } ?: return null
    val claims = jwt.claims
    val userID = claims["userID"]?.asString()?.toIntOrNull()
    val token = claims["token"]?.asString()
    return if (userID != null && token != null && checkToken(userID, token)) {
        EversityJWT.instance.logger.debug("Authenticated user with user ID $userID and token $token")
        User(userID, getUserType(userID), token)
    } else {
        EversityJWT.instance.logger.debug("Rejected authentication to user with ID $userID and token $token")
        null
    }
}
