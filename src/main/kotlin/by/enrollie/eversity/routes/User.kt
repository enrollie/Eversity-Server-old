/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.OSO
import by.enrollie.eversity.controllers.PluginProvider
import by.enrollie.eversity.data_classes.*
import by.enrollie.eversity.data_functions.join
import by.enrollie.eversity.database.functions.*
import by.enrollie.eversity.security.User
import by.enrollie.eversity.uac.OsoUser
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
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
    val lastName: String,
)

private fun Route.usersRoute() {
    get {
        val user = call.authentication.principal<User>()!!
        val routeUserID = call.parameters["userId"]?.toIntOrNull()?.evaluateToUserID(user.user.id)
            ?: throw ParameterConversionException("userId", "userId")
        OSO.authorize(OsoUser(user.user.id, user.user.type), "read", OsoUser(routeUserID, getUserType(routeUserID)))
        val userData = getUserInfo(routeUserID) ?: return@get call.respond(HttpStatusCode.NotFound,
            ErrorResponse.userNotFound(routeUserID))
        call.respond(HttpStatusCode.OK,
            UserInfo(userData.id, userData.type, userData.firstName, userData.middleName, userData.lastName))
    }
}

private fun Route.userClassRoute() {
    get("/class") {
        val user = call.authentication.principal<User>()!!
        val routeUserID = call.parameters["userId"]?.toIntOrNull()?.evaluateToUserID(user.user.id)
            ?: throw ParameterConversionException("userId", "userId")
        OSO.authorize(OsoUser(user.user.id, user.user.type), "read", OsoUser(routeUserID, getUserType(routeUserID)))
        val userClass = when (user.user.type) {
            UserType.Pupil -> {
                getPupilClass(user.user.id)
            }
            UserType.Teacher, UserType.Administration -> {
                getTeacherClass(user.user.id)
            }
            else -> null
        }
        call.respond(mapOf("class" to Json.encodeToJsonElement(userClass),
            "role" to Json.encodeToJsonElement(if (userClass != null) user.user.type.let { if (it == UserType.Administration) UserType.Teacher else it } else null)))
    }
}

private fun Route.userTimetable() {
    route("/timetable") {
        get {
            val user = call.authentication.principal<User>()!!
            val routeUserID = call.parameters["userId"]?.toIntOrNull()?.evaluateToUserID(user.user.id)
                ?: throw ParameterConversionException("userId", "userId")
            OSO.authorize(OsoUser(user.user.id, user.user.type), "read", OsoUser(routeUserID, getUserType(routeUserID)))
            return@get when (getUserType(routeUserID)) {
                UserType.Teacher, UserType.Administration -> {
                    call.respond(getTeacherTimetable(routeUserID).asJsonElement)
                }
                UserType.Pupil -> {
                    call.respond(getClassTimetable(getPupilClass(routeUserID).id).let {
                        if (getPupilClass(routeUserID).isSecondShift) TwoShiftsTimetable(monday = Pair(arrayOf(),
                            it.monday),
                            tuesday = Pair(arrayOf(), it.tuesday),
                            wednesday = Pair(arrayOf(), it.wednesday),
                            thursday = Pair(arrayOf(), it.thursday),
                            friday = Pair(arrayOf(), it.friday),
                            saturday = Pair(arrayOf(), it.saturday)).asJsonElement
                        else TwoShiftsTimetable(monday = Pair(it.monday, arrayOf()),
                            tuesday = Pair(it.tuesday, arrayOf()),
                            wednesday = Pair(it.wednesday, arrayOf()),
                            thursday = Pair(it.thursday, arrayOf()),
                            friday = Pair(it.friday, arrayOf()),
                            saturday = Pair(it.saturday, arrayOf())).asJsonElement
                    })
                }
                else -> call.respond(TwoShiftsTimetable().asJsonElement)
            }
        }
        route("/today") {
            get {
                val user = call.authentication.principal<User>()!!
                val routeUserID = call.parameters["userId"]?.toIntOrNull()?.evaluateToUserID(user.user.id)
                    ?: throw ParameterConversionException("userId", "userId")
                OSO.authorize(OsoUser(user.user.id, user.user.type),
                    "read",
                    OsoUser(routeUserID, getUserType(routeUserID)))
                if (DateTime.now().dayOfWeek == DayOfWeek.SUNDAY.value) return@get call.respond(Json.encodeToJsonElement<Array<Lesson>>(
                    arrayOf()))
                return@get when (getUserType(routeUserID)) {
                    UserType.Teacher, UserType.Administration -> {
                        call.respond(getTeacherTimetable(routeUserID)[DayOfWeek.of(DateTime.now().dayOfWeek)])
                    }
                    UserType.Pupil -> {
                        val pupilClass = getPupilClass(routeUserID)
                        call.respond(getClassTimetable(pupilClass.id).toTwoShiftsTimetable(pupilClass.isSecondShift))
                    }
                    else -> call.respond(TwoShiftsTimetable()[DayOfWeek.of(DateTime.now().dayOfWeek)])
                }
            }
            get("/current") {
                val user = call.authentication.principal<User>()!!
                val routeUserID = call.parameters["userId"]?.toIntOrNull()?.evaluateToUserID(user.user.id)
                    ?: throw ParameterConversionException("userId", "userId")
                OSO.authorize(OsoUser(user.user.id, user.user.type),
                    "read",
                    OsoUser(routeUserID, getUserType(routeUserID)))
                if (DateTime.now().dayOfWeek == DayOfWeek.SUNDAY.value) return@get call.respond(Json.encodeToJsonElement<Lesson?>(
                    null))
                val lessonsList = when (getUserType(routeUserID)) {
                    UserType.Teacher, UserType.Administration -> {
                        getTeacherTimetable(routeUserID)[DayOfWeek.of(DateTime.now().dayOfWeek)]
                    }
                    UserType.Pupil -> {
                        val pupilClass = getPupilClass(routeUserID)
                        getClassTimetable(pupilClass.id).toTwoShiftsTimetable(pupilClass.isSecondShift)[DayOfWeek.of(
                            DateTime.now().dayOfWeek)]
                    }
                    else -> TwoShiftsTimetable()[DayOfWeek.of(DateTime.now().dayOfWeek)]
                }.join()
                val lesson = run {
                    val currTime = DateTime.now()
                    lessonsList.find {
                        Interval(DateTime.now()
                            .withTime(it.schedule.startHour.toInt(), it.schedule.startMinute.toInt(), 0, 0),
                            DateTime.now()
                                .withTime(it.schedule.endHour.toInt(), it.schedule.endMinute.toInt(), 0, 0)).contains(
                            currTime)
                    }
                }
                call.respond(Json.encodeToJsonElement(lesson))
            }
        }
    }
}

@Serializable
private data class IntegrationData(val integrationId: String, val publicName: String, val connected: Boolean)

@Serializable
private data class IntegrationRequest(val integrationId: String)

private fun Route.userIntegrations() {
    route("/integrations") {
        get {
            val user = call.principal<User>()!!
            val routeUserID = call.parameters["userId"]?.toIntOrNull()?.evaluateToUserID(user.user.id)
                ?: throw ParameterConversionException("userId", "userId")
            OSO.authorize(OsoUser(user.user.id, user.user.type),
                "read_integrations",
                OsoUser(routeUserID, getUserType(routeUserID)))
            val availableIntegrations = PluginProvider.getAvailableIntegrationsForUser(routeUserID)
            val connectedIntegrations = PluginProvider.getRegisteredIntegrations(routeUserID)
            call.respond(availableIntegrations.map { integration ->
                IntegrationData(integration.metadata.id,
                    integration.metadata.publicTitle,
                    connectedIntegrations.find { it.metadata.id == integration.metadata.id } != null)
            })
        }
        put {
            val integrationRequest = call.receive<IntegrationRequest>()
            val user = call.principal<User>()!!
            val userID = call.parameters["userId"]?.toIntOrNull()?.evaluateToUserID(user.user.id)
                ?: throw ParameterConversionException("userId", "userId")
            OSO.authorize(OsoUser(user.user.id, user.user.type),
                "create_integrations",
                OsoUser(userID, getUserType(userID)))
            val availableIntegrations = PluginProvider.getAvailableIntegrationsForUser(userID)
            if (availableIntegrations.find { it.metadata.id == integrationRequest.integrationId } == null) return@put call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse.integrationNotFound(integrationRequest.integrationId))
            if (PluginProvider.getRegisteredIntegrations(userID)
                    .find { it.metadata.id == integrationRequest.integrationId } != null
            ) return@put call.respond(HttpStatusCode.Conflict,
                ErrorResponse.conflict("Integration is already connected"))
            call.respond(Json.encodeToJsonElement(PluginProvider.requestRegistration(userID,
                integrationRequest.integrationId)))
        }
        delete {
            val user = call.principal<User>()!!
            val userID = call.parameters["userId"]?.toIntOrNull()?.evaluateToUserID(user.user.id)
                ?: throw ParameterConversionException("userId", "userId")
            OSO.authorize(OsoUser(user.user.id, user.user.type),
                "delete_integrations",
                OsoUser(userID, getUserType(userID)))
            val integrationRequest = call.request.queryParameters.getOrFail("integrationId")
            val integrations = PluginProvider.getRegisteredIntegrations(userID)
            if (integrations.find { it.metadata.id == integrationRequest } == null) return@delete call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse.integrationNotFound(integrationRequest))
            PluginProvider.requestDeletion(userID, integrationRequest)
            call.respond(HttpStatusCode.OK)
        }
    }
}

fun Route.userRoute() {
    route("/user/{userId}") {
        authenticate("jwt") {
            usersRoute()
            userTimetable()
            userIntegrations()
            userClassRoute()
        }
    }
}
