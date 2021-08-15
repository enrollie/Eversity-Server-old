/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*

fun Route.pingRouting(){
    get ("/api/ping"){
        call.respondText("Pong!", status = HttpStatusCode.OK)
    }
}

fun Application.configurePingRouting(){
    routing {
        pingRouting()
    }
}