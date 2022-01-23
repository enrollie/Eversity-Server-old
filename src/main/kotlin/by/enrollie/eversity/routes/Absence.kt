/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.DATE_FORMAT
import by.enrollie.eversity.data_classes.AbsenceReason
import by.enrollie.eversity.data_classes.AbsenceStatisticsPackage
import by.enrollie.eversity.data_classes.ClassID
import by.enrollie.eversity.data_classes.UserID
import by.enrollie.eversity.data_functions.tryToParse
import by.enrollie.eversity.database.functions.getAbsenceStatistics
import by.enrollie.eversity.database.functions.getDetailedAbsenceData
import by.enrollie.eversity.database.functions.getPupilsCount
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.joda.time.LocalDate
import org.joda.time.LocalTime
import org.joda.time.format.DateTimeFormat

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
            val date = call.request.queryParameters["date"]?.let {
                DateTimeFormat.forPattern(DATE_FORMAT).tryToParse(it)?.withTime(LocalTime.MIDNIGHT)
                    ?: throw ParameterConversionException("date",
                        "date")
            } ?: LocalDate.now().toDateTime(LocalTime.MIDNIGHT)
            val statistics = getAbsenceStatistics(date).let {
                Pair(AbsenceStatisticsPackage.fromMap(it.first), AbsenceStatisticsPackage.fromMap(it.second))
            }
            val pupilsCount = getPupilsCount()
            call.respond(ShortAbsenceResponse(pupilsCount, statistics))
        }
        get("/detailed") {
            val date = call.request.queryParameters["date"]?.let {
                DateTimeFormat.forPattern(DATE_FORMAT).tryToParse(it)?.withTime(LocalTime.MIDNIGHT)
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

fun Route.absenceRoute() {
    authenticate("jwt") {
        route("/absence") {
            statistics()
        }
    }
}
