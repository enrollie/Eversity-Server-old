/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.data_classes.APIUserType
import by.enrollie.eversity.data_classes.Pupil
import by.enrollie.eversity.database.functions.*
import by.enrollie.eversity.security.User
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
private data class AnonymousMark(val markNum: Short?, val lessonPlace: Short?)

@Serializable
private data class PupilData(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val marksList: List<AnonymousMark>
)

fun Route.diaryRoute() {
    authenticate("jwt") {
        route("/api/timetable") {
            get("/class/{id}") {
                val user = call.authentication.principal<User>() ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    "Authentication failed. Check your token."
                )
                val classID = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    "Class ID was not found or malformed"
                )
                if (user.type == APIUserType.Pupil) {
                    if (getPupilClass(user.id) != classID) {
                        return@get call.respond(
                            HttpStatusCode.Forbidden,
                            "User does not have access to this class"
                        )
                    }
                }
                if (user.type == APIUserType.Parent) {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        "Parents does not have access to classes"
                    )
                }
                if (!doesClassExist(classID)) {
                    return@get call.respond(
                        HttpStatusCode.NotFound,
                        "Class $classID was not found"
                    )
                }
                return@get call.respondText(
                    contentType = ContentType.Application.Json, text = Json.encodeToString(
                        getClassTimetable(classID)
                    )
                )
            }
            get("/teacher/{id}") {
                val teacherID = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    "Class ID was not found or malformed"
                )
                val user = call.authentication.principal<User>() ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    "Authentication failed. Check your token."
                )
                if (user.type == APIUserType.Parent || user.type == APIUserType.Pupil)
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        "You cannot access this route"
                    )
                if (!doesUserExist(teacherID))
                    return@get call.respond(
                        HttpStatusCode.NotFound,
                        "User with ID $teacherID was not found"
                    )
                if (getUserType(teacherID) != APIUserType.Teacher)
                    return@get call.respond(
                        HttpStatusCode.NotFound,
                        "User with ID $teacherID is not a teacher"
                    )
                return@get call.respondText(
                    contentType = ContentType.Application.Json, text = Json.encodeToString(
                        getTeacherTimetable(teacherID)
                    )
                )
            }
            get("/pupil/{id}") {
                val pupilID = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    "Class ID was not found or malformed"
                )
                val user = call.authentication.principal<User>() ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    "Authentication failed. Check your token."
                )
                if (user.type == APIUserType.Parent || user.id != pupilID)
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        "You cannot access this route"
                    )
                if (!doesUserExist(pupilID))
                    return@get call.respond(
                        HttpStatusCode.NotFound,
                        "User with ID $pupilID was not found"
                    )
                if (getUserType(pupilID) != APIUserType.Pupil)
                    return@get call.respond(
                        HttpStatusCode.NotFound,
                        "User with ID $pupilID is not a pupil"
                    )
                return@get call.respondText(
                    contentType = ContentType.Application.Json, text = Json.encodeToString(
                        getPupilTimetable(pupilID)
                    )
                )
            }
        }
        route("/api/journal") {
            get("/class/{id}") {
                val classID = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    "Class ID was not found or malformed"
                )
                val user = call.authentication.principal<User>() ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    "Authentication failed. Check your token."
                )
                if (user.type == APIUserType.Teacher && !validateTeacherAccessToClass(user.id, classID))
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        "You cannot access this route"
                    )
                if (user.type == APIUserType.Parent || user.type == APIUserType.Pupil)
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        "You cannot access this route"
                    )
                if (!doesClassExist(classID)) {
                    return@get call.respond(
                        HttpStatusCode.NotFound,
                        "Class with ID $classID was not found"
                    )
                }
                val data = getClassJournal(classID)
                val timetable = getClassTimetable(classID)
                val map = mutableMapOf<Pupil, PupilData>()
                data.first.forEach {
                    map[it] = PupilData(it.id, it.firstName, it.lastName, listOf())
                }
                data.second.forEach {
                    val temp =
                        (map[it.pupil] ?: PupilData(it.pupil.id, it.pupil.firstName, it.pupil.lastName, listOf()))
                    map[it.pupil] = temp.copy(marksList = temp.marksList + AnonymousMark(it.markNum, it.lessonPlace))
                }
                call.respondText(
                    Json.encodeToString(
                        mapOf(
                            "timetable" to Json.encodeToJsonElement(timetable),
                            "pupilsData" to Json.encodeToJsonElement(map.toList().map { it.second }.toSet())
                        )
                    )
                )
            }
        }
    }
}

fun Application.registerDiary() {
    routing {
        diaryRoute()
    }
}