/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.DefaultDateFormatter
import by.enrollie.eversity.OSO
import by.enrollie.eversity.data_classes.AbsenceReason
import by.enrollie.eversity.data_classes.AbsenceStatisticsPackage
import by.enrollie.eversity.data_classes.ClassID
import by.enrollie.eversity.data_classes.UserID
import by.enrollie.eversity.data_functions.tryToParse
import by.enrollie.eversity.database.functions.getAbsenceStatistics
import by.enrollie.eversity.database.functions.getDetailedAbsenceData
import by.enrollie.eversity.database.functions.getNoAbsenceDataClasses
import by.enrollie.eversity.database.functions.getPupilsCount
import by.enrollie.eversity.security.User
import by.enrollie.eversity.uac.OsoUser
import by.enrollie.eversity.uac.School
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.joda.time.LocalDate
import org.joda.time.LocalTime

@Serializable
private data class ShortAbsenceResponse(
    val pupilCount: Pair<Int, Int>,
    val absenceStatistics: Pair<AbsenceStatisticsPackage, AbsenceStatisticsPackage>,
)

@Serializable
private data class DetailedAbsenceResponseElement(
    val id: UserID,
    val firstName: String,
    val middleName: String?,
    val lastName: String,
    val absenceReason: AbsenceReason,
    @SerialName("classId")
    val classID: ClassID,
)

private fun Route.statistics() {
    route("/statistics") {
        get {
            val user = call.authentication.principal<User>()!!
            OSO.authorize(OsoUser(user.user.id, user.user.type), "read_whole_absence", School())
            val date = call.request.queryParameters["date"]?.let {
                DefaultDateFormatter.tryToParse(it)?.withTime(LocalTime.MIDNIGHT)
                    ?: throw ParameterConversionException("date",
                        "date")
            } ?: LocalDate.now().toDateTime(LocalTime.MIDNIGHT)
            val statistics = getAbsenceStatistics(date).let {
                Pair(AbsenceStatisticsPackage.fromMap(it.first), AbsenceStatisticsPackage.fromMap(it.second))
            }
            val pupilsCount = getPupilsCount(date)
            call.respond(ShortAbsenceResponse(pupilsCount, statistics))
        }
        get("/detailed") {
            val user = call.authentication.principal<User>()!!
            OSO.authorize(OsoUser(user.user.id, user.user.type), "read_whole_absence", School())
            val date = call.request.queryParameters["date"]?.let {
                DefaultDateFormatter.tryToParse(it)?.withTime(LocalTime.MIDNIGHT)
                    ?: throw ParameterConversionException("date",
                        "date")
            } ?: LocalDate.now().toDateTime(LocalTime.MIDNIGHT)
            val data = getDetailedAbsenceData(date).map {
                DetailedAbsenceResponseElement(it.first.id,
                    it.first.firstName,
                    it.first.middleName,
                    it.first.lastName,
                    it.second.reason,
                    it.first.classID)
            }
            call.respond(data)
        }
    }
}

private fun Route.noData() {
    route("/noData") {
        get {
            val user = call.authentication.principal<User>()!!
            OSO.authorize(OsoUser(user.user.id, user.user.type), "read_whole_absence", School())
            val date = call.request.queryParameters["date"]?.let {
                DefaultDateFormatter.tryToParse(it)?.withTime(LocalTime.MIDNIGHT)
                    ?: throw ParameterConversionException("date",
                        "date")
            } ?: LocalDate.now().toDateTime(LocalTime.MIDNIGHT)
            call.respond(getNoAbsenceDataClasses(date))
        }
    }
}

fun Route.absenceRoute() {
    authenticate("jwt") {
        route("/absence") {
            statistics()
            noData()
        }
    }
}
