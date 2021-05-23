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

fun Route.pupilRouting() {
    route("/api") {
        get("/info/{id}") {
            val id = call.parameters["id"] ?: return@get call.respondText(
                "No id supplied",
                contentType = ContentType.Text.Plain,
                status = HttpStatusCode.BadRequest
            )
//            val pupil = pupilList.find { it.id == id.toInt() }
//            if (pupil == null) {
//                return@get call.respondText(
//                    "Pupil with id $id is not found",
//                    contentType = ContentType.Text.Plain,
//                    status = HttpStatusCode.NotFound
//                )
//            } else {
//                call.respond(pupil)
//            }
            call.respond("CUM")
        }
    }
}

fun Application.registerPupilsRouting() {
    routing {
       pupilRouting()
    }
}