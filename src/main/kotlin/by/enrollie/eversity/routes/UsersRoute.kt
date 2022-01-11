/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.data_classes.UserType
import by.enrollie.eversity.database.functions.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

@OptIn(ExperimentalSerializationApi::class)
fun Route.usersRouting() {
    route("/api/") {
        route("user") {
            authenticate("jwt") {
                get {
                    val userJWT =
                        call.authentication.principal<by.enrollie.eversity.security.User>() ?: return@get call.respond(
                            HttpStatusCode.Unauthorized,
                            "Authentication failed. Check your token."
                        )
                    if (!doesUserExist(userJWT.id))
                        return@get call.respondText(
                            "User with ID ${userJWT.id} was not found.",
                            status = HttpStatusCode.NotFound
                        )
                    val name = getUserName(userJWT.id)
                    return@get call.respondText(
                        contentType = ContentType.Application.Json, text = Json.encodeToString(
                            mapOf(
                                "id" to Json.encodeToJsonElement(userJWT.id.toString()),
                                "type" to Json.encodeToJsonElement(userJWT.type.name),
                                "firstName" to Json.encodeToJsonElement(name.firstName),
                                "middleName" to Json.encodeToJsonElement(name.middleName),
                                "lastName" to Json.encodeToJsonElement(name.lastName)
                            )
                        )
                    )
                }
                get("/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respondText(
                        "Missing or malformed ID",
                        status = HttpStatusCode.BadRequest
                    )
                    if (!doesUserExist(id)) {
                        return@get call.respondText("User with ID $id was not found.", status = HttpStatusCode.NotFound)
                    }
                    val userType = getUserType(id)
                    val name = getUserName(id)
                    return@get call.respondText(
                        contentType = ContentType.Application.Json, text = Json.encodeToString(
                            mapOf(
                                "id" to Json.encodeToJsonElement(id.toString()),
                                "type" to Json.encodeToJsonElement(userType.name),
                                "firstName" to Json.encodeToJsonElement(name.firstName),
                                "middleName" to Json.encodeToJsonElement(name.middleName),
                                "lastName" to Json.encodeToJsonElement(name.lastName)
                            )
                        )
                    )
                }
            }
        }
        route("pupil") {
            authenticate("jwt") {
                get("/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respondText(
                        "Missing or malformed ID",
                        status = HttpStatusCode.BadRequest
                    )
                    if (getUserType(id) != UserType.Pupil) {
                        return@get call.respond(
                            HttpStatusCode.BadRequest,
                            "User with ID $id is not a pupil"
                        )
                    }
                    val classID = getPupilClass(id).toString()
                    val classData = getClass(id)
                    return@get call.respond(
                        Json.encodeToString(
                            mapOf(
                                "classID" to classID,
                                "classTitle" to classData.title,
                                "classTeacherID" to classData.classTeacherID.toString()
                            )
                        )
                    )
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
