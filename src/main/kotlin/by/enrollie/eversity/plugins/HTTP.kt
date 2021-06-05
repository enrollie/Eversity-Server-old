/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.plugins

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*

private const val ISSUER = "Enrollie-EversityJWT"
private const val AUDIENCE = "Enrollie/EversityJWT"

fun Application.configureHTTP() {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
        header(HttpHeaders.Server, "Eversity Core")
    }

}
