/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.EVERSITY_PUBLIC_NAME
import by.enrollie.eversity.SCHOOL_NAME
import by.enrollie.eversity.database.functions.getAllClasses
import by.enrollie.eversity.database.functions.getAllUsers
import by.enrollie.eversity.database.functions.getPupilsInClass
import by.enrollie.eversity.database.functions.getUserName
import by.enrollie.eversity.serializers.DateTimeSerializer
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.joda.time.DateTime

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
                    getPupilsInClass(it.id).size,
                    it.classTeacherID,
                    getUserName(it.classTeacherID).fullForm
                )
            })
        }
    }
}

fun Route.schoolRoute() {
    route("/school") {
        aboutSchool()
    }
}
