/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.N_Placer
import by.enrollie.eversity.data_classes.APIPlaceJob
import by.enrollie.eversity.data_classes.Pupil
import by.enrollie.eversity.data_classes.UserType
import by.enrollie.eversity.data_functions.fillAbsenceTemplate
import by.enrollie.eversity.database.functions.*
import by.enrollie.eversity.placer.data_classes.PlaceJob
import by.enrollie.eversity.security.User
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.joda.time.DateTime
import org.joda.time.IllegalFieldValueException
import org.joda.time.format.DateTimeFormat
import java.text.SimpleDateFormat
import java.util.*


@Serializable
private data class ClassData(val classID: Int, val className: String, val isSecondShift: Boolean)

fun Route.absenceRoute() {
    authenticate("jwt") {
        route("/api/absence") {
            get("/statistics") {
                val user = call.authentication.principal<User>() ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    "Authentication failed. Check your token."
                )
                if (user.type != UserType.Social && user.type != UserType.Administration)
                    return@get call.respondText(
                        text = Json.encodeToString(
                            mapOf(
                                "errorCode" to "ACTION_IS_UNAUTHORIZED",
                                "action" to "NOTHING"
                            )
                        )
                    )
                val pupilCount = countPupils()
                val absenceStatistics = getAbsenceStatistics(DateTime.now().withTimeAtStartOfDay())
                return@get call.respondText(
                    text = Json.encodeToString(
                        mapOf(
                            "pupilCount" to Json.encodeToJsonElement(pupilCount),
                            "absenceStatistics" to Json.encodeToJsonElement(absenceStatistics)
                        )
                    )
                )
            }
            get("/statistics/day/{date}") {
                val user = call.authentication.principal<User>() ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    "Authentication failed. Check your token."
                )
                if (user.type != UserType.Social && user.type != UserType.Administration)
                    return@get call.respondText(
                        text = Json.encodeToString(
                            mapOf(
                                "errorCode" to "ACTION_IS_UNAUTHORIZED",
                                "action" to "NOTHING"
                            )
                        )
                    )
                val date =
                    call.parameters["date"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Date is missing")
                val parsedDate = try {
                    val parser = DateTimeFormat.forPattern("YYYY-MM-dd")
                    DateTime.parse(date, parser)
                } catch (e: IllegalArgumentException) {
                    return@get call.respondText(text = "Malformed date. Reminder: required date format: YYYY-MM-dd")
                }
                val pupilCount = countPupils()
                val absenceStatistics = getAbsenceStatistics(parsedDate)
                return@get call.respondText(
                    text = Json.encodeToString(
                        mapOf(
                            "pupilCount" to Json.encodeToJsonElement(pupilCount),
                            "absenceStatistics" to Json.encodeToJsonElement(absenceStatistics)
                        )
                    )
                )
            }
            post("/class/{id}") {
                val user = call.authentication.principal<User>() ?: return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    "Authentication failed. Check your token."
                )
                val classID = call.parameters["id"]?.toIntOrNull() ?: return@post call.respondText(
                    "Missing or malformed class ID",
                    status = HttpStatusCode.BadRequest
                )
                if (user.type == UserType.Parent || user.type == UserType.Pupil) {
                    this.application.log.info("User with ID ${user.id} and type ${user.type} tried to post absence")
                    return@post call.respondText(
                        text = Json.encodeToString(
                            mapOf(
                                "errorCode" to "ACTION_IS_UNAUTHORIZED",
                                "action" to "NOTHING"
                            )
                        ), status = HttpStatusCode.Forbidden
                    )
                }
                val absenceJob: Array<APIPlaceJob>
                absenceJob = try {
                    val req = call.receive<String>()
                    Json.decodeFromString(req)
                } catch (e: ContentTransformationException) {
                    application.log.debug("Deserialization failed. ${e.message}", e)
                    return@post call.respond(HttpStatusCode.BadRequest, "Deserialization failed: ${e.localizedMessage}")
                } catch (e: SerializationException) {
                    application.log.debug("Deserialization failed", e)
                    return@post call.respond(HttpStatusCode.BadRequest, "Malformed JSON")
                } catch (e: IllegalArgumentException) {
                    application.log.debug("Deserialization failed", e)
                    return@post call.respond(HttpStatusCode.BadRequest, "Malformed JSON")
                }
                if (absenceJob.filter { it.classID == classID }.size != absenceJob.size) {
                    return@post call.respondText(
                        text = Json.encodeToString(
                            mapOf(
                                "errorCode" to "REQUEST_CLASS_ID_NOT_SAME_FOR_ALL",
                                "action" to "REVIEW_REQUEST"
                            )
                        ), status = HttpStatusCode.BadRequest
                    )
                }
                if (absenceJob.filter { if (it.pupilID != -1) it.classID == getPupilClass(it.pupilID) else true }.size != absenceJob.size) {
                    return@post call.respondText(
                        text = Json.encodeToString(
                            mapOf(
                                "errorCode" to "NOT_ALL_PUPILS_MATCH_THEIR_CLASS_ID",
                                "action" to "REVIEW_REQUEST"
                            )
                        ), status = HttpStatusCode.BadRequest
                    )
                }
                if (absenceJob.filter { if (it.pupilID != -1) getUserType(it.pupilID) == UserType.Pupil else true }.size != absenceJob.size) {
                    return@post call.respondText(
                        text = Json.encodeToString(
                            mapOf(
                                "errorCode" to "NOT_ALL_USER_ID_MATCH_PUPIL_TYPE",
                                "additionalInfo" to "REVIEW_REQUEST"
                            )
                        ), status = HttpStatusCode.BadRequest
                    )
                }
                try {
                    absenceJob.forEach {
                        if (DateTime.parse(it.date).dayOfWeek == 7) {
                            return@post call.respondText(
                                text = Json.encodeToString(
                                    mapOf(
                                        "errorCode" to "ABSENCE_POST_SUNDAY",
                                        "additionalInfo" to "REVIEW_REQUEST"
                                    )
                                ), status = HttpStatusCode.BadRequest
                            )
                        }
                    }
                } catch (e: IllegalFieldValueException) {
                    return@post call.respondText(
                        text = Json.encodeToString(
                            mapOf(
                                "errorCode" to "MALFORMED_DATE",
                                "additionalInfo" to "REVIEW_REQUEST"
                            )
                        ), status = HttpStatusCode.BadRequest
                    )
                } catch (e: IllegalArgumentException) {
                    return@post call.respondText(
                        text = Json.encodeToString(
                            mapOf(
                                "errorCode" to "MALFORMED_DATE",
                                "action" to "REVIEW_REQUEST"
                            )
                        ), status = HttpStatusCode.BadRequest
                    )
                }
                val placeList = mutableSetOf<PlaceJob>()
                for (absence in absenceJob) {
                    val name = getUserName(absence.pupilID)
                    val placementJob = PlaceJob(
                        Pupil(absence.pupilID, name.firstName, name.middleName, name.lastName, classID),
                        absence.absenceList,
                        user.id,
                        absence.reason,
                        DateTime.parse(absence.date).withTimeAtStartOfDay(),
                        null // TODO: Add parsing of additional notes
                    )
                    placeList += placementJob
                }
                N_Placer.postAbsence(placeList.toList())
                return@post call.respond(HttpStatusCode.OK)
            }
            get("/statistics/ready") {
                val user = call.authentication.principal<User>() ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    "Authentication failed. Check your token."
                )
                if (user.type != UserType.Social && user.type != UserType.Administration) {
                    this.application.log.info("User with ID ${user.id} and type ${user.type} tried to access absence route")
                    return@get call.respondText(
                        text = Json.encodeToString(
                            mapOf(
                                "errorCode" to "ACTION_IS_UNAUTHORIZED",
                                "action" to "NOTHING"
                            )
                        ), status = HttpStatusCode.Forbidden
                    )
                }
                val noDataClasses = getNoAbsenceDataClasses(DateTime.now().withTimeAtStartOfDay())
                return@get call.respondText(
                    text = Json.encodeToString(
                        mapOf(
                            "isReady" to Json.encodeToJsonElement(
                                noDataClasses.isEmpty()
                            ), "noData" to Json.encodeToJsonElement(noDataClasses.map {
                                ClassData(it.id, it.title, it.isSecondShift)
                            })
                        )
                    )
                )
            }
            get("/statistics/fill") {
                val user = call.authentication.principal<User>() ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    "Authentication failed. Check your token."
                )
                if (user.type != UserType.Social && user.type != UserType.Administration) {
                    this.application.log.info("User with ID ${user.id} and type ${user.type} tried to access absence route")
                    return@get call.respondText(
                        text = Json.encodeToString(
                            mapOf(
                                "errorCode" to "ACTION_IS_UNAUTHORIZED",
                                "action" to "NOTHING"
                            )
                        ), status = HttpStatusCode.Forbidden
                    )
                }
                val absenceData = getAbsenceStatistics(DateTime.now().withTimeAtStartOfDay())
                val pupilCount = countPupils()
                val filledTemplate = fillAbsenceTemplate(
                    absenceData,
                    pupilCount,
                    this.javaClass.getResourceAsStream("/absence_template.docx")!!
                )
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        "eversity-data-${SimpleDateFormat("YYYY-MM-dd--HH-mm-ss").format(Calendar.getInstance().time)}.docx"
                    ).toString()
                )
                call.respondFile(filledTemplate)
                filledTemplate.deleteOnExit()
            }
            get("/statistics/fill/{date}") {
                val user = call.authentication.principal<User>() ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    "Authentication failed. Check your token."
                )
                val date = try {
                    val dateString = call.parameters["date"] ?: return@get call.respondText(
                        text = "Date is missing",
                        status = HttpStatusCode.BadRequest
                    )
                    val parser = DateTimeFormat.forPattern("YYYY-MM-dd")
                    DateTime.parse(dateString, parser)
                } catch (e: IllegalArgumentException) {
                    return@get call.respondText(text = "Malformed date. Reminder: required date format: YYYY-MM-dd")
                }
                if (user.type != UserType.Social && user.type != UserType.Administration) {
                    this.application.log.info("User with ID ${user.id} and type ${user.type} tried to access absence route")
                    return@get call.respondText(
                        text = Json.encodeToString(
                            mapOf(
                                "errorCode" to "ACTION_IS_UNAUTHORIZED",
                                "action" to "NOTHING"
                            )
                        ), status = HttpStatusCode.Forbidden
                    )
                }
                val absences = getAbsenceStatistics(date)
                val pupilsCount = countPupils()
                val filledTemplate = fillAbsenceTemplate(
                    absences,
                    pupilsCount,
                    this.javaClass.getResourceAsStream("/absence_template.docx")!!,
                    date.toString("dd.MM.YYYY")
                )
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        "eversity-data-${SimpleDateFormat("YYYY-MM-dd--HH-mm-ss").format(Calendar.getInstance().time)}.docx"
                    ).toString()
                )
                call.respondFile(filledTemplate)
                filledTemplate.deleteOnExit()
            }
        }
    }
}

fun Application.registerAbsenceRoute() {
    routing {
        absenceRoute()
    }
}

