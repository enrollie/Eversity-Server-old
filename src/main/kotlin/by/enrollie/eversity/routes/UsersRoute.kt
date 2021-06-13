/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.controllers.DataController
import by.enrollie.eversity.data_classes.APIUserType
import by.enrollie.eversity.data_classes.DayOfWeek
import by.enrollie.eversity.data_classes.Lesson
import by.enrollie.eversity.data_classes.User
import by.enrollie.eversity.database.EversityDatabase
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

fun Route.usersRouting() {
    route("/api/user") {
        authenticate("jwt") {
            get("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respondText(
                    "Missing or malformed ID",
                    status = HttpStatusCode.BadRequest
                )
                if (!EversityDatabase.doesUserExist(id)) {
                    return@get call.respondText("User with ID $id was not found.", status = HttpStatusCode.NotFound)
                }
                val userType = EversityDatabase.getUserType(id)
                val name = EversityDatabase.getUserName(userID = id, userType)
                when (userType) {
                    APIUserType.Pupil -> {
                        val timetable = EversityDatabase.getPupilTimetable(id)
                        return@get call.respondText(
                            contentType = ContentType.Application.Json, text = Json.encodeToString(
                                mapOf(
                                    "id" to Json.encodeToJsonElement(id.toString()),
                                    "type" to Json.encodeToJsonElement(userType.name),
                                    "firstName" to Json.encodeToJsonElement(name.first),
                                    "middleName" to Json.encodeToJsonElement(name.second),
                                    "lastName" to Json.encodeToJsonElement(name.third),
                                    "timetable" to Json.encodeToJsonElement(timetable)
                                )
                            )
                        )
                    }
                    APIUserType.Teacher -> {
                        val timetable = EversityDatabase.getTeacherTimetable(id)
                        return@get call.respondText(
                            contentType = ContentType.Application.Json, text = Json.encodeToString(
                                mapOf(
                                    "id" to Json.encodeToJsonElement(id.toString()),
                                    "type" to Json.encodeToJsonElement(userType.name),
                                    "firstName" to Json.encodeToJsonElement(name.first),
                                    "middleName" to Json.encodeToJsonElement(name.second),
                                    "lastName" to Json.encodeToJsonElement(name.third),
                                    "timetable" to Json.encodeToJsonElement(timetable)
                                )
                            )
                        )
                    }
                    else -> {
                        val timetable = mutableMapOf<DayOfWeek, Array<Lesson>>(
                            DayOfWeek.MONDAY to arrayOf(),
                            DayOfWeek.TUESDAY to arrayOf(),
                            DayOfWeek.WEDNESDAY to arrayOf(),
                            DayOfWeek.THURSDAY to arrayOf(),
                            DayOfWeek.FRIDAY to arrayOf(),
                            DayOfWeek.SATURDAY to arrayOf(),
                        )
                        return@get call.respondText(
                            contentType = ContentType.Application.Json, text = Json.encodeToString(
                                mapOf(
                                    "id" to Json.encodeToJsonElement(id.toString()),
                                    "type" to Json.encodeToJsonElement(userType.name),
                                    "firstName" to Json.encodeToJsonElement(name.first),
                                    "middleName" to Json.encodeToJsonElement(name.second),
                                    "lastName" to Json.encodeToJsonElement(name.third),
                                    "timetable" to Json.encodeToJsonElement(timetable)
                                )
                            )
                        )
                    }
                }
            }
            post("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull() ?: return@post call.respondText(
                    "Missing or malformed ID",
                    status = HttpStatusCode.BadRequest
                )
                val userJWT =
                    call.authentication.principal<by.enrollie.eversity.security.User>() ?: return@post call.respond(
                        HttpStatusCode.Forbidden,
                        "Authentication failed. Check your token."
                    )
                if (!EversityDatabase.doesUserExist(id)) {
                    return@post call.respondText("User with ID $id was not found.", status = HttpStatusCode.NotFound)
                }
                val requestedUser = User(id, EversityDatabase.getUserType(id))
                try {
                    DataController().updateUser(requestedUser)
                    return@post call.respond(HttpStatusCode.OK)
                } catch (e: IllegalStateException) {
                    if (userJWT.id == requestedUser.id) {
                        EversityDatabase.invalidateAllTokens(requestedUser.id, "INVALID_CREDENTIALS_ON_UPDATE")
                        return@post call.respond(
                            HttpStatusCode.PreconditionFailed,
                            Json.encodeToJsonElement(
                                mapOf(
                                    "errorCode" to "INVALID_COOKIES",
                                    "action" to "INVALIDATE_ALL_TOKENS"
                                )
                            )
                        )
                    } else {
                        return@post call.respond(
                            HttpStatusCode.PreconditionFailed,
                            Json.encodeToJsonElement(
                                mapOf(
                                    "errorCode" to "REQUEST_USER_INVALID_COOKIES",
                                    "action" to "NOTHING"
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}

fun Application.registerUsersRoute() {
    routing {
        usersRouting()
    }
}