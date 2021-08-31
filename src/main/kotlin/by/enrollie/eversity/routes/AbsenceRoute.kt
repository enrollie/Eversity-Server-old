/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.N_Placer
import by.enrollie.eversity.data_classes.APIPlaceJob
import by.enrollie.eversity.data_classes.APIUserType
import by.enrollie.eversity.data_classes.Pupil
import by.enrollie.eversity.data_functions.fillAbsenceTemplate
import by.enrollie.eversity.database.functions.*
import by.enrollie.eversity.placer.data_classes.PlaceJob
import by.enrollie.eversity.placer.data_classes.PlacingStatus
import by.enrollie.eversity.security.User
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.*
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
import javax.security.auth.login.CredentialException


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
                if (user.type != APIUserType.Social && user.type != APIUserType.Administration)
                    return@get call.respondText(
                        text = Json.encodeToString(
                            mapOf(
                                "errorCode" to "ACTION_IS_UNAUTHORIZED",
                                "action" to "NOTHING"
                            )
                        )
                    )
                val pupilCount = countPupils()
                val absenceStatistics = getAbsenceStatistics()
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
                if (user.type != APIUserType.Social && user.type != APIUserType.Administration)
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
                if (user.type == APIUserType.Parent || user.type == APIUserType.Pupil) {
                    this.application.log.info("User with ID ${user.id} and type ${user.type} tried to post absence")
                    return@post call.respondText(
                        text = Json.encodeToString(
                            mapOf(
                                "errorCode" to "ACTION_IS_UNAUTHORIZED",
                                "action" to "NOTHING"
                            )
                        ), status = HttpStatusCode.BadRequest
                    )
                }
                try {
                    if (user.type == APIUserType.Teacher) {
                        if (!validateTeacherWriteAccessToClass(user.id, classID))
                            return@post call.respondText(
                                text = Json.encodeToString(
                                    mapOf(
                                        "errorCode" to "UNAUTHORIZED_ACTION",
                                        "action" to "NOTHING"
                                    )
                                ), status = HttpStatusCode.BadRequest
                            )
                    }
                } catch (e: IllegalArgumentException) {
                    return@post call.respondText(
                        text = Json.encodeToString(
                            mapOf(
                                "errorCode" to "CLASS_DOES_NOT_EXIST",
                                "action" to "REVIEW_REQUEST"
                            )
                        ), status = HttpStatusCode.BadRequest
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
                if (absenceJob.filter { if (it.pupilID != -1) getUserType(it.pupilID) == APIUserType.Pupil else true }.size != absenceJob.size) {
                    return@post call.respondText(
                        text = Json.encodeToString(
                            mapOf(
                                "errorCode" to "NOT_ALL_USER_ID_MATCH_PUPIL_TYPE",
                                "action" to "REVIEW_REQUEST"
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
                                        "action" to "REVIEW_REQUEST"
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
                                "action" to "REVIEW_REQUEST"
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
                val statusID: String = if (absenceJob.size == 1) {
                    val name = getUserName(absenceJob[0].pupilID, APIUserType.Pupil)
                    val credentials = try {
                        obtainCredentials(user.id)
                    } catch (e: NoSuchElementException) {
                        invalidateAllTokens(user.id, "INVALID_COOKIES")
                        return@post call.respondText(
                            text = Json.encodeToString(
                                mapOf(
                                    "errorCode" to "INVALID_CREDENTIALS",
                                    "action" to "INVALIDATE_ALL_TOKENS"
                                )
                            ), status = HttpStatusCode.PreconditionFailed
                        )
                    }
                    if (credentials.first == null || credentials.second == null) {
                        invalidateAllTokens(user.id, "INVALID_COOKIES")
                        return@post call.respondText(
                            text = Json.encodeToString(
                                mapOf(
                                    "errorCode" to "INVALID_CREDENTIALS",
                                    "action" to "INVALIDATE_ALL_TOKENS"
                                )
                            ), status = HttpStatusCode.PreconditionFailed
                        )
                    }
                    val placementJob = PlaceJob(
                        Pupil(absenceJob[0].pupilID, name.first, name.third, classID),
                        absenceJob[0].absenceList,
                        absenceJob[0].reason,
                        Pair(
                            Pair(credentials.first!!, credentials.second!!), credentials.third
                        ), absenceJob[0].date
                    )
                    try {
                        N_Placer.addPlacementJob(placementJob)
                    } catch (e: CredentialException) {
                        invalidateAllTokens(user.id, "INVALID_COOKIES")
                        return@post call.respond(
                            HttpStatusCode.PreconditionFailed,
                            Json.encodeToJsonElement(
                                mapOf(
                                    "errorCode" to "INVALID_COOKIES",
                                    "action" to "INVALIDATE_ALL_TOKENS"
                                )
                            )
                        )
                    }
                } else {
                    val placeList = mutableSetOf<PlaceJob>()
                    for (absence in absenceJob) {
                        val name = getUserName(absence.pupilID, APIUserType.Pupil)
                        val credentials = obtainCredentials(user.id)
                        if (credentials.first == null || credentials.second == null) {
                            invalidateAllTokens(user.id, "INVALID_CREDENTIALS")
                            return@post call.respondText(
                                text = Json.encodeToString(
                                    mapOf(
                                        "errorCode" to "INVALID_CREDENTIALS",
                                        "action" to "INVALIDATE_ALL_TOKENS"
                                    )
                                ), status = HttpStatusCode.PreconditionFailed
                            )
                        }
                        val placementJob = PlaceJob(
                            Pupil(absence.pupilID, name.first, name.third, classID),
                            absence.absenceList,
                            absence.reason,
                            Pair(
                                Pair(credentials.first!!, credentials.second!!), credentials.third
                            ), absence.date
                        )
                        placeList += placementJob
                    }
                    try {
                        N_Placer.batchPlacementJobs(placeList.toList())
                    } catch (e: CredentialException) {
                        invalidateAllTokens(user.id, "INVALID_COOKIES")
                        return@post call.respond(
                            HttpStatusCode.PreconditionFailed,
                            Json.encodeToJsonElement(
                                mapOf(
                                    "errorCode" to "INVALID_COOKIES",
                                    "action" to "INVALIDATE_ALL_TOKENS"
                                )
                            )
                        )
                    }
                }
                return@post call.respondText(
                    text = Json.encodeToString(
                        mapOf(
                            "type" to if (statusID.startsWith("gr")) "group" else "single",
                            "ID" to statusID
                        )
                    ),
                    status = HttpStatusCode.OK
                )
            }
            get("/statistics/ready") {
                val user = call.authentication.principal<User>() ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    "Authentication failed. Check your token."
                )
                if (user.type != APIUserType.Social) {
                    this.application.log.info("User with ID ${user.id} and type ${user.type} tried to access absence route")
                    return@get call.respondText(
                        text = Json.encodeToString(
                            mapOf(
                                "errorCode" to "ACTION_IS_UNAUTHORIZED",
                                "action" to "NOTHING"
                            )
                        ), status = HttpStatusCode.BadRequest
                    )
                }
                val noDataClasses = getNoAbsenceDataClasses()
                return@get call.respondText(
                    text = Json.encodeToString(
                        mapOf(
                            "isReady" to Json.encodeToJsonElement(
                                noDataClasses.isEmpty()
                            ), "noData" to Json.encodeToJsonElement(noDataClasses.map {
                                ClassData(it.first, it.second, it.third)
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
                if (user.type != APIUserType.Social) {
                    this.application.log.info("User with ID ${user.id} and type ${user.type} tried to access absence route")
                    return@get call.respondText(
                        text = Json.encodeToString(
                            mapOf(
                                "errorCode" to "ACTION_IS_UNAUTHORIZED",
                                "action" to "NOTHING"
                            )
                        ), status = HttpStatusCode.BadRequest
                    )
                }
                val absenceData = getAbsenceStatistics()
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
                if (user.type != APIUserType.Social) {
                    this.application.log.info("User with ID ${user.id} and type ${user.type} tried to access absence route")
                    return@get call.respondText(
                        text = Json.encodeToString(
                            mapOf(
                                "errorCode" to "ACTION_IS_UNAUTHORIZED",
                                "action" to "NOTHING"
                            )
                        ), status = HttpStatusCode.BadRequest
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
                call.respondBytes(filledTemplate.readBytes())
                filledTemplate.deleteOnExit()
            }
            webSocket("/subscribe") {
                var subscribedID: String? = null
                var isGroupJob = false
                for (frame in incoming) {
                    if (subscribedID != null) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "JOB ID IS ALREADY SET"))
                    }
                    when (frame) {
                        is Frame.Text -> {
                            val receivedID = frame.readText()
                            if (subscribedID != null) {
                                send("JOB SUBSCRIPTION IS ALREADY SENT")
                                continue
                            }
                            subscribedID = receivedID.removeSuffix("\n")
                            if (subscribedID != "all" && !N_Placer.checkIfJobExists(subscribedID)) {
                                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "NO SUCH JOB ID WERE FOUND"))
                                return@webSocket
                            }
                            if (subscribedID.startsWith("jb")) {
                                val doneJob = N_Placer.checkJobStatus(subscribedID)
                                if (doneJob == PlacingStatus.DONE || doneJob == PlacingStatus.ERROR) {
                                    send(Json.encodeToString(Pair(subscribedID, doneJob)))
                                    close(
                                        CloseReason(
                                            CloseReason.Codes.NORMAL,
                                            "JOB IS DONE. NO FURTHER DATA IS AVAILABLE"
                                        )
                                    )
                                    return@webSocket
                                }
                                if (doneJob == PlacingStatus.RUNNING)
                                    send(Json.encodeToString(Pair(subscribedID, doneJob)))
                            } else if (subscribedID.startsWith("gr")) {
                                isGroupJob = true
                                val doneJobs = N_Placer.checkJobGroupStatus(subscribedID)
                                for (job in doneJobs.filter { it.value == PlacingStatus.RUNNING }) {
                                    send(Json.encodeToString(job.toPair()))
                                }
                                for (job in doneJobs.filter { it.value == PlacingStatus.ERROR || it.value == PlacingStatus.DONE }) {
                                    send(Json.encodeToString(job.toPair()))
                                }
                                if (doneJobs.count { it.value == PlacingStatus.DONE || it.value == PlacingStatus.ERROR } == doneJobs.size) {
                                    close(
                                        CloseReason(
                                            CloseReason.Codes.NORMAL,
                                            "ALL JOBS ARE DONE. NO FURTHER DATA IS AVAILABLE"
                                        )
                                    )
                                }
                            } else {
                                if (subscribedID != "all")
                                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "JOB ID IS MALFORMED"))
                            }
                            send("subscribed")
                            val channel = N_Placer.jobsStatusBroadcast.openSubscription()
                            var received: Pair<String, PlacingStatus>
                            while (true) {
                                received = channel.receive()
                                if (subscribedID == "all") {
                                    send(Json.encodeToString(received))
                                    continue
                                }
                                if (isGroupJob) {
                                    val jobsID = N_Placer.getGroupJobs(subscribedID)
                                    if (jobsID.contains(received.first)) {
                                        send(Json.encodeToString(received))
                                    }
                                    if (N_Placer.checkJobGroupStatus(subscribedID)
                                            .count { it.value == PlacingStatus.DONE || it.value == PlacingStatus.ERROR } == jobsID.size
                                    ) {
                                        close(
                                            CloseReason(
                                                CloseReason.Codes.NORMAL,
                                                "ALL JOBS ARE DONE. NO FURTHER DATA IS AVAILABLE"
                                            )
                                        )
                                        channel.cancel()
                                        break
                                    }
                                } else if (received.first == subscribedID) {
                                    send(Json.encodeToString(received))
                                    if (received.second == PlacingStatus.DONE || received.second == PlacingStatus.ERROR) {
                                        close(
                                            CloseReason(
                                                CloseReason.Codes.NORMAL,
                                                "JOB IS DONE. NO FURTHER DATA IS AVAILABLE"
                                            )
                                        )
                                        break
                                    }
                                }
                            }
                            channel.cancel()
                        }
                    }
                }
            }
        }
    }
}

fun Application.registerAbsenceRoute() {
    routing {
        absenceRoute()
    }
}

