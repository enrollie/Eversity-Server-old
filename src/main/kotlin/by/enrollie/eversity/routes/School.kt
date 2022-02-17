/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.DefaultDateFormatter
import by.enrollie.eversity.EVERSITY_PUBLIC_NAME
import by.enrollie.eversity.OSO
import by.enrollie.eversity.SCHOOL_NAME
import by.enrollie.eversity.data_classes.UserType
import by.enrollie.eversity.data_functions.tryToParse
import by.enrollie.eversity.database.functions.getAllClasses
import by.enrollie.eversity.database.functions.getAllUsers
import by.enrollie.eversity.database.functions.getPupilsInClass
import by.enrollie.eversity.database.functions.getUserName
import by.enrollie.eversity.security.User
import by.enrollie.eversity.serializers.DateTimeSerializer
import by.enrollie.eversity.uac.OsoUser
import by.enrollie.eversity.uac.School
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.joda.time.DateTime
import org.joda.time.LocalTime
import org.slf4j.LoggerFactory

@kotlinx.serialization.Serializable
private data class SchoolStatistics(val usersCount: Int, val classesCount: Int)

@kotlinx.serialization.Serializable
private data class SchoolData(
    val title: String, val serverVersion: String,
    @kotlinx.serialization.Serializable(DateTimeSerializer::class)
    val localTime: DateTime,
    val statistics: SchoolStatistics
)

private fun Route.aboutSchool() {
    get {
        call.respond(
            SchoolData(
                SCHOOL_NAME.genitive, EVERSITY_PUBLIC_NAME, DateTime.now(),
                SchoolStatistics(getAllUsers().size, getAllClasses().size)
            )
        )
    }
    authenticate("jwt")
    {
        get("/classes") {
            call.respond(getAllClasses().map {
                ResponseSchoolClass(
                    it.id,
                    it.title,
                    it.isSecondShift,
                    getPupilsInClass(DateTime.now().withTime(LocalTime.MIDNIGHT), it.id).size,
                    it.classTeacherID,
                    getUserName(it.classTeacherID).fullForm
                )
            })
        }
        get("/users") {
            val user = call.authentication.principal<User>()!!
            OSO.authorize(OsoUser(user.user.id, user.user.type), "read_all_profiles", School())
            val date = call.request.queryParameters["date"]?.let {
                DefaultDateFormatter.tryToParse(it) ?: throw ParameterConversionException("date", "date")
            } ?: DateTime.now().withTime(LocalTime.MIDNIGHT)
            val types = (call.request.queryParameters["type"]?.let {
                try {
                    it.split(",").map { type ->
                        LoggerFactory.getLogger("USERS-GET").debug(type)
                        UserType.lenientValueOf(type)
                    }
                } catch (e: IllegalArgumentException) {
                    throw ParameterConversionException("type", "array[UserType]", e)
                }
            } ?: UserType.values().toList()).map {
                it.name
            }
            call.respond(getAllUsers(date, types))
        }
    }
}

fun Route.schoolRoute() {
    route("/school") {
        aboutSchool()
    }
}
