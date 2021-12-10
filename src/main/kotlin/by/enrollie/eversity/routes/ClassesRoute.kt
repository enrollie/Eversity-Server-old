/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.data_classes.APIUserType
import by.enrollie.eversity.data_functions.fillClassAbsenceTemplate
import by.enrollie.eversity.database.functions.*
import by.enrollie.eversity.security.User
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import org.joda.time.format.DateTimeFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

fun Route.classesRoute() {
    authenticate("jwt") {
        route("/api/class/{id}") {
            get {
                val classID = call.parameters["id"]?.toIntOrNull() ?: return@get call.respondText(
                    "Missing or malformed ID",
                    status = HttpStatusCode.BadRequest
                )
                if (!doesClassExist(classID))
                    return@get call.respondText(
                        text = "Class with ID $classID was not found in this instance",
                        status = HttpStatusCode.NotFound
                    )
                val classData = getClass(classID)
                return@get call.respondText(
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK,
                    text = Json.encodeToString(classData)
                )
            }
            get("/timetable") {
                val classID = call.parameters["id"]?.toIntOrNull() ?: return@get call.respondText(
                    "Missing or malformed ID",
                    status = HttpStatusCode.BadRequest
                )
                if (!doesClassExist(classID))
                    return@get call.respondText(
                        text = "Class with ID $classID was not found in this instance",
                        status = HttpStatusCode.NotFound
                    )
                val classTimetable = getClassTimetable(classID)
                return@get call.respondText(
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK,
                    text = Json.encodeToString(classTimetable)
                )
            }
            get("/pupils") {
                val classID = call.parameters["id"]?.toIntOrNull() ?: return@get call.respondText(
                    "Missing or malformed ID",
                    status = HttpStatusCode.BadRequest
                )
                if (!doesClassExist(classID))
                    return@get call.respondText(
                        text = "Class with ID $classID was not found in this instance",
                        status = HttpStatusCode.NotFound
                    )
                val classPupils = getClassPupils(classID)
                return@get call.respondText(
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK,
                    text = Json.encodeToString(classPupils)
                )
            }
            get("/absence") {
                val classID = call.parameters["id"]?.toIntOrNull() ?: return@get call.respondText(
                    "Missing or malformed ID",
                    status = HttpStatusCode.BadRequest
                )
                if (!doesClassExist(classID))
                    return@get call.respondText(
                        text = "Class with ID $classID was not found in this instance",
                        status = HttpStatusCode.NotFound
                    )
                val absences = getClassAbsence(classID)
                return@get call.respondText(
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK,
                    text = Json.encodeToString(absences)
                )
            }
            get("/absence/statistics") {
                if ((call.request.queryParameters["startDate"] != null && call.request.queryParameters["endDate"] == null) ||
                    (call.request.queryParameters["startDate"] == null && call.request.queryParameters["endDate"] != null) // one of parameters is present, but other one is not
                ) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Date range is malformed")
                }
                val classID = call.parameters["id"]?.toIntOrNull() ?: return@get call.respondText(
                    "Missing or malformed ID",
                    status = HttpStatusCode.BadRequest
                )
                val user = call.authentication.principal<User>() ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    "Authentication failed. Check your token."
                )
                when (user.type) {
                    APIUserType.Parent, APIUserType.Pupil -> return@get call.respond(HttpStatusCode.Forbidden)
                    APIUserType.Teacher -> {
                        if (getClass(classID).classTeacherID != user.id)
                            return@get call.respond(HttpStatusCode.Forbidden)
                    }
                    else -> {}
                }
                val beginDate =
                    call.request.queryParameters["startDate"]?.let {
                        DateTimeFormat.forPattern("YYYY-MM-dd").parseDateTime(it)
                    }
                        ?: DateTime.now().withDayOfWeek(DateTimeConstants.MONDAY)
                val endDate = call.request.queryParameters["endDate"]?.let {
                    DateTimeFormat.forPattern("YYYY-MM-dd").parseDateTime(it)
                }
                    ?: DateTime.now().withDayOfWeek(DateTimeConstants.SATURDAY)
                val absencePackage = getClassStatistics(classID, beginDate, endDate)
                val resultFile = fillClassAbsenceTemplate(
                    this.javaClass.getResourceAsStream("/classAbsenceReport.docx")!!,
                    absencePackage,
                    classID,
                    Pair(beginDate.toString("dd.MM.YYYY"), endDate.toString("dd.MM.YYYY"))
                )
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        "Eversity-class-$classID-${SimpleDateFormat("YYYY-MM-dd--HH-mm-ss").format(Calendar.getInstance().time)}.docx"
                    ).toString()
                )
                call.respondFile(resultFile)
                resultFile.delete()
            }
            get("/absence/day/{date}") {
                val classID = call.parameters["id"]?.toIntOrNull() ?: return@get call.respondText(
                    "Missing or malformed ID",
                    status = HttpStatusCode.BadRequest
                )
                if (!doesClassExist(classID))
                    return@get call.respondText(
                        text = "Class with ID $classID was not found in this instance",
                        status = HttpStatusCode.NotFound
                    )
                val date = call.parameters["date"] ?: return@get call.respondText(
                    "Missing date",
                    status = HttpStatusCode.BadRequest
                )
                try {
                    val dateFormatter = DateTimeFormat.forPattern("YYYY-MM-dd")
                    dateFormatter.parseDateTime(date)
                } catch (e: IllegalArgumentException) {
                    return@get call.respondText("Malformed date", status = HttpStatusCode.BadRequest)
                }
                val absences = getClassAbsence(classID, date)
                return@get call.respondText(
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK,
                    text = Json.encodeToString(absences)
                )
            }
            get("/absence/week/{date}") {
                val classID = call.parameters["id"]?.toIntOrNull() ?: return@get call.respondText(
                    "Missing or malformed ID",
                    status = HttpStatusCode.BadRequest
                )
                if (!doesClassExist(classID))
                    return@get call.respondText(
                        text = "Class with ID $classID was not found in this instance",
                        status = HttpStatusCode.NotFound
                    )
                val date = call.parameters["date"] ?: return@get call.respondText(
                    "Missing date",
                    status = HttpStatusCode.BadRequest
                )
                val dateFormatter = DateTimeFormat.forPattern("YYYY-MM-dd")
                val startDay: String
                val endDay: String
                try {
                    var dateTime = dateFormatter.parseDateTime(date)
                    dateTime = dateTime.minusDays(abs(DateTimeConstants.MONDAY - dateTime.dayOfWeek))
                    startDay = dateTime.toString("YYYY-MM-dd")
                    dateTime = dateTime.plusDays(6)
                    endDay = dateTime.toString("YYYY-MM-dd")
                } catch (e: IllegalArgumentException) {
                    return@get call.respondText("Malformed date", status = HttpStatusCode.BadRequest)
                }
                val absences = getClassAbsence(classID, startDay, endDay)
                return@get call.respondText(
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK,
                    text = Json.encodeToString(absences)
                )
            }
        }
    }
}

fun Application.registerClassesRoute() {
    routing { classesRoute() }
}