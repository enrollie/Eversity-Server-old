/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.data_classes.*
import by.enrollie.eversity.data_functions.join
import by.enrollie.eversity.database.functions.*
import by.enrollie.eversity.security.User
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.joda.time.DateTime
import org.joda.time.Interval
import java.time.DayOfWeek

@Serializable
private data class UserInfo(
    val id: UserID,
    val type: UserType,
    val firstName: String,
    val middleName: String?,
    val lastName: String
)

private fun Route.usersRoute() {
    get {
        val routeUserID =
            call.parameters["userId"]?.toIntOrNull()?.evaluateToUserID(call.authentication.principal<User>()!!.id)
                ?: throw ParameterConversionException("userId", "userId")
        val userData = getUserInfo(routeUserID) ?: return@get call.respond(
            HttpStatusCode.NotFound,
            ErrorResponse.userNotFound(routeUserID)
        )
        call.respond(
            HttpStatusCode.OK,
            UserInfo(userData.id, userData.type, userData.firstName, userData.middleName, userData.lastName)
        )
    }
}

private fun Route.userTimetable() {
    route("/timetable") {
        get {
            val routeUserID =
                call.parameters["userId"]?.toIntOrNull()?.evaluateToUserID(call.authentication.principal<User>()!!.id)
                    ?: throw ParameterConversionException("userId", "userId")
            return@get when (getUserType(routeUserID)) {
                UserType.Teacher, UserType.Administration -> {
                    call.respond(getTeacherTimetable(routeUserID).asJsonElement)
                }
                UserType.Pupil -> {
                    call.respond(getClassTimetable(getPupilClass(routeUserID)).let {
                        if (getClass(getPupilClass(routeUserID)).isSecondShift)
                            TwoShiftsTimetable(
                                monday = Pair(arrayOf(), it.monday),
                                tuesday = Pair(arrayOf(), it.tuesday),
                                wednesday = Pair(arrayOf(), it.wednesday),
                                thursday = Pair(arrayOf(), it.thursday),
                                friday = Pair(arrayOf(), it.friday),
                                saturday = Pair(arrayOf(), it.saturday)
                            ).asJsonElement
                        else TwoShiftsTimetable(
                            monday = Pair(it.monday, arrayOf()),
                            tuesday = Pair(it.tuesday, arrayOf()),
                            wednesday = Pair(it.wednesday, arrayOf()),
                            thursday = Pair(it.thursday, arrayOf()),
                            friday = Pair(it.friday, arrayOf()),
                            saturday = Pair(it.saturday, arrayOf())
                        ).asJsonElement
                    })
                }
                else -> call.respond(TwoShiftsTimetable().asJsonElement)
            }
        }
        route("/today") {
            get {
                val routeUserID = call.parameters["userId"]?.toIntOrNull()
                    ?.evaluateToUserID(call.authentication.principal<User>()!!.id)
                    ?: throw ParameterConversionException("userId", "userId")
                return@get when (getUserType(routeUserID)) {
                    UserType.Teacher, UserType.Administration -> {
                        call.respond(
                            getTeacherTimetable(routeUserID)[DayOfWeek.of(DateTime.now().dayOfWeek)]
                        )
                    }
                    UserType.Pupil -> {
                        call.respond(getClassTimetable(getPupilClass(routeUserID)).let {
                            if (getClass(getPupilClass(routeUserID)).isSecondShift)
                                TwoShiftsTimetable(
                                    monday = Pair(arrayOf(), it.monday),
                                    tuesday = Pair(arrayOf(), it.tuesday),
                                    wednesday = Pair(arrayOf(), it.wednesday),
                                    thursday = Pair(arrayOf(), it.thursday),
                                    friday = Pair(arrayOf(), it.friday),
                                    saturday = Pair(arrayOf(), it.saturday)
                                )[DayOfWeek.of(DateTime.now().dayOfWeek)]
                            else TwoShiftsTimetable(
                                monday = Pair(it.monday, arrayOf()),
                                tuesday = Pair(it.tuesday, arrayOf()),
                                wednesday = Pair(it.wednesday, arrayOf()),
                                thursday = Pair(it.thursday, arrayOf()),
                                friday = Pair(it.friday, arrayOf()),
                                saturday = Pair(it.saturday, arrayOf())
                            )[DayOfWeek.of(DateTime.now().dayOfWeek)]
                        })
                    }
                    else -> call.respond(
                        TwoShiftsTimetable()[DayOfWeek.of(DateTime.now().dayOfWeek)]
                    )
                }
            }
            get("/current") {
                val routeUserID = call.parameters["userId"]?.toIntOrNull()
                    ?.evaluateToUserID(call.authentication.principal<User>()!!.id)
                    ?: throw ParameterConversionException("userId", "userId")
                val lessonsList = when (getUserType(routeUserID)) {
                    UserType.Teacher, UserType.Administration -> {
                        getTeacherTimetable(routeUserID)[DayOfWeek.of(DateTime.now().dayOfWeek)]
                    }
                    UserType.Pupil -> {
                        getClassTimetable(getPupilClass(routeUserID)).let {
                            if (getClass(getPupilClass(routeUserID)).isSecondShift)
                                TwoShiftsTimetable(
                                    monday = Pair(arrayOf(), it.monday),
                                    tuesday = Pair(arrayOf(), it.tuesday),
                                    wednesday = Pair(arrayOf(), it.wednesday),
                                    thursday = Pair(arrayOf(), it.thursday),
                                    friday = Pair(arrayOf(), it.friday),
                                    saturday = Pair(arrayOf(), it.saturday)
                                )[DayOfWeek.of(DateTime.now().dayOfWeek)]
                            else TwoShiftsTimetable(
                                monday = Pair(it.monday, arrayOf()),
                                tuesday = Pair(it.tuesday, arrayOf()),
                                wednesday = Pair(it.wednesday, arrayOf()),
                                thursday = Pair(it.thursday, arrayOf()),
                                friday = Pair(it.friday, arrayOf()),
                                saturday = Pair(it.saturday, arrayOf())
                            )[DayOfWeek.of(DateTime.now().dayOfWeek)]
                        }
                    }
                    else ->
                        TwoShiftsTimetable()[DayOfWeek.of(DateTime.now().dayOfWeek)]
                }.join()
                val lesson = run {
                    val currTime = DateTime.now()
                    lessonsList.find {
                        Interval(
                            DateTime.now()
                                .withTime(it.schedule.startHour.toInt(), it.schedule.startMinute.toInt(), 0, 0),
                            DateTime.now().withTime(it.schedule.endHour.toInt(), it.schedule.endMinute.toInt(), 0, 0)
                        ).contains(currTime)
                    }
                }
                call.respond(Json.encodeToJsonElement(lesson))
            }
        }
    }
}

private fun Route.userIntegrations() {
    route("/ingerations") {
        get {

        }
    }
}

fun Route.userRoute() {
    route("/user/{userId}") {
        authenticate("jwt") {
            usersRoute()
            userTimetable()
        }
    }
}
