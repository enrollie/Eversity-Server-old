/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.plugins

import by.enrollie.eversity.database.functions.checkToken
import by.enrollie.eversity.database.functions.getUserInfo
import by.enrollie.eversity.security.EversityJWT
import by.enrollie.eversity.security.User
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureAuthentication() {
    install(Authentication) {
        jwt("jwt") {
            verifier(EversityJWT.instance.verifier)
            realm = "Eversity-Core/JWT"
            validate { jwtCredential ->
                val userID = jwtCredential.payload.getClaim("userID").asString().toIntOrNull()
                val token = jwtCredential.payload.getClaim("token").asString()
                return@validate if (userID != null && checkToken(userID, token)) {
                    User(getUserInfo(userID)!!, token)
                } else {
                    null
                }
            }
        }
    }
}
