/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.plugins

import by.enrollie.eversity.database.EversityDatabase
import by.enrollie.eversity.security.EversityJWT
import by.enrollie.eversity.security.User
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.http.*

fun Application.configureAuthentication() {
    install(Authentication) {
        jwt("jwt") {
            verifier(EversityJWT.instance.verifier)
            validate { jwtCredential ->
                log.error(jwtCredential.payload.claims.toString())
                val userID = jwtCredential.payload.getClaim("userID").asString().toInt()
                val token = jwtCredential.payload.getClaim("token").asString()
                if (EversityDatabase.checkToken(userID, token).first){
                    User(userID, EversityDatabase.getUserType(userID))
                }else{
                    null
                }
            }
        }
    }
}