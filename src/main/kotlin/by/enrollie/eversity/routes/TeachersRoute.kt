/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.data_classes.APIUserType
import by.enrollie.eversity.data_classes.DayOfWeek
import by.enrollie.eversity.data_classes.TeacherLesson
import by.enrollie.eversity.database.functions.*
import by.enrollie.eversity.security.User
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Interval
import org.joda.time.MutableDateTime

fun Route.teachersRoute() {
    authenticate("jwt") {
        route("/api/teacher/{id}") {
            get("/class") {
                val userJWT = call.authentication.principal<User>() ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    "Authentication failed. Check your token."
                )
                if (userJWT.type == APIUserType.Parent || userJWT.type == APIUserType.Pupil) {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        "You are not authorized to do this"
                    )
                }
                val teacherID = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    "Teacher ID is absent or malformed"
                )
                if (!doesUserExist(teacherID)) return@get call.respond(
                    HttpStatusCode.NotFound,
                    "User with ID $teacherID was not found"
                )
                if (getUserType(teacherID) != APIUserType.Teacher && getUserType(teacherID) != APIUserType.Administration) return@get call.respond(
                    HttpStatusCode.BadRequest,
                    "User with ID $teacherID is not a teacher"
                )
                val teacherClass = getTeacher(teacherID)
                val classData = teacherClass.second.let { if (it != null) getClass(it) else null }
                call.respond(
                    HttpStatusCode.OK, mapOf(
                        "classID" to Json.encodeToJsonElement(classData?.id),
                        "className" to Json.encodeToJsonElement(classData?.title)
                    )
                )
            }
            get("/current") {
                val userJWT = call.authentication.principal<User>() ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    "Authentication failed. Check your token."
                )
                if (userJWT.type == APIUserType.Parent || userJWT.type == APIUserType.Pupil) {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        "You are not authorized to do this"
                    )
                }
                val teacherID = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    "Teacher ID is absent or malformed"
                )
                if (!doesUserExist(teacherID)) return@get call.respond(
                    HttpStatusCode.NotFound,
                    "User with ID $teacherID was not found"
                )
                if (getUserType(teacherID) != APIUserType.Teacher && getUserType(teacherID) != APIUserType.Administration) return@get call.respond(
                    HttpStatusCode.BadRequest,
                    "User with ID $teacherID is not a teacher"
                )
                val timetable = getTeacherTimetable(teacherID)
                val lessons = mutableListOf<TeacherLesson>()
                timetable.toList().forEach {
                    if (it != null) {
                        val day = it[DayOfWeek.values()[DateTime.now().dayOfWeek - 1]]
                        if (day != null) {
                            val lesson = day.find {
                                Interval(MutableDateTime().apply {
                                    hourOfDay = it.schedule.startHour.toInt()
                                    minuteOfHour = it.schedule.startMinute.toInt()
                                }, MutableDateTime().apply {
                                    if (it.schedule.startHour > it.schedule.endHour)
                                        addDays(1)
                                    hourOfDay = it.schedule.endHour.toInt()
                                    minuteOfHour = it.schedule.endMinute.toInt()
                                }).contains(DateTime.now(DateTimeZone.getDefault()))
                            }
                            if (lesson!=null)
                                lessons.add(lesson)
                        }
                    }
                }
                call.respond(
                    HttpStatusCode.OK, mapOf(
                        "currentLesson" to Json.encodeToJsonElement(lessons.firstOrNull())
                    )
                )
            }
        }
    }
}

fun Application.registerTeachers() {
    routing {
        teachersRoute()
    }
}