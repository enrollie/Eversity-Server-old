/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.DefaultDateFormatter
import by.enrollie.eversity.OSO
import by.enrollie.eversity.SERVER_CONFIGURATION
import by.enrollie.eversity.data_functions.fillAbsenceTemplate
import by.enrollie.eversity.data_functions.fillClassAbsenceTemplate
import by.enrollie.eversity.data_functions.tryToParse
import by.enrollie.eversity.database.functions.countPupils
import by.enrollie.eversity.database.functions.getAbsenceStatistics
import by.enrollie.eversity.database.functions.getClassStatistics
import by.enrollie.eversity.security.User
import by.enrollie.eversity.uac.OsoSchoolClass
import by.enrollie.eversity.uac.OsoUser
import by.enrollie.eversity.uac.School
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.joda.time.DateTime
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

private enum class TemplateTypes { SchoolAbsenceReport, ClassAbsenceReport }

private val timer = Timer("FileDeleter")

private fun Route.generate() {
    get("/generate") {
        val user = call.authentication.principal<User>()!!
        val type = call.request.queryParameters["type"] ?: throw MissingRequestParameterException("type")
        when (type.lowercase()) {
            TemplateTypes.ClassAbsenceReport.name.lowercase() -> {
                val classID = call.request.queryParameters["classId"]?.toIntOrNull()
                    ?: throw MissingRequestParameterException("classId")
                OSO.authorize(OsoUser(user.user.id, user.user.type), "read_absence", OsoSchoolClass(classID))
                val firstDate = call.request.queryParameters["firstDate"]?.let {
                    DefaultDateFormatter.tryToParse(it) ?: throw ParameterConversionException("firstDate", "date")
                } ?: throw MissingRequestParameterException("firstDate")
                val lastDate = call.request.queryParameters["lastDate"]?.let {
                    DefaultDateFormatter.tryToParse(it) ?: throw ParameterConversionException("lastDate", "date")
                } ?: throw MissingRequestParameterException("lastDate")
                val absences = getClassStatistics(classID, firstDate, lastDate)
                val docFile = withContext(Dispatchers.IO) {
                    File.createTempFile("ClassAbsence", ".docx", SERVER_CONFIGURATION.documentTempDir).apply {
                        createNewFile()
                    }
                }
                fillClassAbsenceTemplate(
                    this::class.java.getResourceAsStream("/classAbsenceReport.docx")!!, absences, classID, Pair(
                        firstDate.toString(
                            "dd.MM.YYYY"
                        ), lastDate.toString("dd.MM.YYYY")
                    ), docFile
                )
                timer.schedule(object : TimerTask() {
                    override fun run() {
                        docFile.delete()
                    }
                }, SERVER_CONFIGURATION.documentsLifetime)
                call.respond(
                    mapOf(
                        "validFor" to Json.encodeToJsonElement(
                            TimeUnit.MILLISECONDS.toSeconds(
                                SERVER_CONFIGURATION.documentsLifetime
                            )
                        ),
                        "downloadLink" to Json.encodeToJsonElement("${SERVER_CONFIGURATION.baseDocumentUrl}/${docFile.name}"),
                        "title" to Json.encodeToJsonElement(
                            "Eversity-ClassReport-$classID-${
                                DateTime.now().toString("YYYY-mm-dd--HH-mm")
                            }.docx"
                        )
                    )
                )
            }
            TemplateTypes.SchoolAbsenceReport.name.lowercase() -> {
                OSO.authorize(OsoUser(user.user.id, user.user.type), "read_whole_absence", School())
                withContext(Dispatchers.IO) { SERVER_CONFIGURATION.documentTempDir.mkdir() }
                val date = call.request.queryParameters["date"]?.let {
                    DefaultDateFormatter.tryToParse(it) ?: throw ParameterConversionException("date", "date")
                } ?: throw MissingRequestParameterException("date")
                val data = getAbsenceStatistics(date)
                val docFile = withContext(Dispatchers.IO) {
                    File.createTempFile("SchoolAbsence", ".docx", SERVER_CONFIGURATION.documentTempDir).apply {
                        createNewFile()
                    }
                }
                fillAbsenceTemplate(
                    data,
                    countPupils(),
                    DateTime.now(),
                    this::class.java.getResourceAsStream("/absenceTemplate.docx")!!,
                    docFile
                )
                timer.schedule(object : TimerTask() {
                    override fun run() {
                        docFile.delete()
                    }
                }, SERVER_CONFIGURATION.documentsLifetime)
                call.respond(
                    mapOf(
                        "validFor" to Json.encodeToJsonElement(
                            TimeUnit.MILLISECONDS.toSeconds(
                                SERVER_CONFIGURATION.documentsLifetime
                            )
                        ),
                        "downloadLink" to Json.encodeToJsonElement("${SERVER_CONFIGURATION.baseDocumentUrl}/${docFile.name}"),
                        "title" to Json.encodeToJsonElement(
                            "Eversity-SchoolReport-${
                                DateTime.now().toString("YYYY-mm-dd--HH-mm")
                            }.docx"
                        )
                    )
                )
            }
            else -> throw ParameterConversionException("type", "templateType")
        }
    }
}

fun Route.templatingRoute() {
    route("/templating") {
        authenticate("jwt") {
            generate()
        }
    }
}
