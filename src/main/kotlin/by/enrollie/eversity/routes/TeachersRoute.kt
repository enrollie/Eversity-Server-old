/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.data_classes.DayOfWeek
import by.enrollie.eversity.data_classes.UserType
import by.enrollie.eversity.database.functions.doesUserExist
import by.enrollie.eversity.database.functions.getTeacherClass
import by.enrollie.eversity.database.functions.getTeacherTimetable
import by.enrollie.eversity.database.functions.getUserType
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
                if (userJWT.type == UserType.Parent || userJWT.type == UserType.Pupil) {
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
                if (getUserType(teacherID) != UserType.Teacher && getUserType(teacherID) != UserType.Administration) return@get call.respond(
                    HttpStatusCode.BadRequest,
                    "User with ID $teacherID is not a teacher"
                )
                val classData = getTeacherClass(teacherID)
                call.respond(
                    HttpStatusCode.OK, mapOf(
                        "classID" to Json.encodeToJsonElement(classData?.id),
                        "className" to Json.encodeToJsonElement(classData?.title),
                        "isSecondShift" to Json.encodeToJsonElement(classData?.isSecondShift)
                    )
                )
            }
            get("/current") {
                val userJWT = call.authentication.principal<User>() ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    "Authentication failed. Check your token."
                )
                if (userJWT.type == UserType.Parent || userJWT.type == UserType.Pupil) {
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
                if (getUserType(teacherID) != UserType.Teacher && getUserType(teacherID) != UserType.Administration) return@get call.respond(
                    HttpStatusCode.BadRequest,
                    "User with ID $teacherID is not a teacher"
                )
                val timetable = getTeacherTimetable(teacherID)
                val currentDayTimetable = timetable.get(DayOfWeek.values()[DateTime.now().dayOfWeek - 1])
                call.respond(
                    HttpStatusCode.OK, mapOf(
                        "currentLesson" to Json.encodeToJsonElement((currentDayTimetable.first + currentDayTimetable.second).find {
                            Interval(MutableDateTime().apply {
                                hourOfDay = it.schedule.startHour.toInt()
                                minuteOfHour = it.schedule.startMinute.toInt()
                            }, MutableDateTime().apply {
                                if (it.schedule.startHour > it.schedule.endHour)
                                    addDays(1)
                                hourOfDay = it.schedule.endHour.toInt()
                                minuteOfHour = it.schedule.endMinute.toInt()
                            }).contains(DateTime.now(DateTimeZone.getDefault()))
                        })
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
