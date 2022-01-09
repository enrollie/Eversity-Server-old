/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.data_classes.AbsenceReason
import by.enrollie.eversity.data_classes.Pupil
import by.enrollie.eversity.data_classes.UserType
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
import org.joda.time.DateTime

@Serializable
private data class AnonymousMark(val markNum: Short?, val lessonPlace: Short?)

@Serializable
private data class PupilData(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val absenceReason: AbsenceReason?,
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
                if (user.type == UserType.Pupil) {
                    if (getPupilClass(user.id) != classID) {
                        return@get call.respond(
                            HttpStatusCode.Forbidden,
                            "User does not have access to this class"
                        )
                    }
                }
                if (user.type == UserType.Parent) {
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
                if (user.type == UserType.Parent || user.type == UserType.Pupil)
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        "You cannot access this route"
                    )
                if (!doesUserExist(teacherID))
                    return@get call.respond(
                        HttpStatusCode.NotFound,
                        "User with ID $teacherID was not found"
                    )
                if (getUserType(teacherID) != UserType.Teacher)
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
                if (user.type == UserType.Parent || user.id != pupilID)
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        "You cannot access this route"
                    )
                if (!doesUserExist(pupilID))
                    return@get call.respond(
                        HttpStatusCode.NotFound,
                        "User with ID $pupilID was not found"
                    )
                if (getUserType(pupilID) != UserType.Pupil)
                    return@get call.respond(
                        HttpStatusCode.NotFound,
                        "User with ID $pupilID is not a pupil"
                    )
                return@get call.respondText(
                    contentType = ContentType.Application.Json, text = Json.encodeToString(
                        getClassTimetable(getPupilClass(pupilID))
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
                if (!doesClassExist(classID)) {
                    return@get call.respond(
                        HttpStatusCode.NotFound,
                        "Class with ID $classID was not found"
                    )
                }
                if (user.type == UserType.Teacher) {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        "You cannot access this route"
                    )
                }
                if (user.type == UserType.Parent || user.type == UserType.Pupil)
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        "You cannot access this route"
                    )
                val data = getClassAbsence(classID, DateTime.now())
                val pupilsList = getPupilsInClass(classID)
                val timetable = getClassTimetable(classID)
                val map = mutableMapOf<Pupil, PupilData>()
                pupilsList.forEach {
                    map[it] = PupilData(it.id, it.firstName, it.lastName, null, listOf())
                }
                data.forEach { absence ->
                    val temp = map.entries.find { it.key.id == absence.pupilID }?.let {
                        it.setValue(
                            it.value.copy(
                                absenceReason = absence.reason,
                                marksList = absence.lessonsList.map { AnonymousMark(-1, it) })
                        )
                    }
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
