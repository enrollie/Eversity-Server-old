/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.AbsencePlacer
import by.enrollie.eversity.DATE_FORMAT
import by.enrollie.eversity.data_classes.*
import by.enrollie.eversity.data_functions.areNulls
import by.enrollie.eversity.data_functions.tryToParse
import by.enrollie.eversity.database.functions.*
import by.enrollie.eversity.exceptions.SchoolClassConstraintsViolation
import by.enrollie.eversity.placer.data_classes.PlaceJob
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.joda.time.DateTime
import org.joda.time.Interval
import org.joda.time.LocalDate
import org.joda.time.LocalTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.ISODateTimeFormat
import org.slf4j.LoggerFactory
import java.time.DayOfWeek

@Serializable
private data class ResponseSchoolClass(
    val id: Int,
    val title: String,
    val isSecondShift: Boolean,
    val pupilCount: Int,
    val classTeacherId: Int,
    val classTeacherName: String,
)

private fun Route.classGet() {
    get {
        val classID = (call.parameters["classId"] ?: throw MissingRequestParameterException("classId")).toIntOrNull()
            ?: throw ParameterConversionException("classId", "classId")
        val classData = getClass(classID)
        call.respond(ResponseSchoolClass(classID,
            classData.title,
            classData.isSecondShift,
            getPupilsInClass(classID).size,
            classData.classTeacherID,
            getUserName(classData.classTeacherID).fullForm))
    }
}

@Serializable
private data class AbsencePair(val pupil: Pupil, val absences: Set<Absence>)

private fun Route.classAbsence() {
    route("/absence") {
        get {
            val classID =
                (call.parameters["classId"] ?: throw MissingRequestParameterException("classId")).toIntOrNull()
                    ?: throw ParameterConversionException("classId", "classId")
            val dates = call.request.queryParameters["startDate"]?.let {
                val endDate =
                    call.request.queryParameters["endDate"] ?: return@get call.respond(HttpStatusCode.BadRequest,
                        ErrorResponse.conditionalMissingRequiredQuery("startDate", "endDate"))
                val parser = DateTimeFormat.forPattern(DATE_FORMAT)
                Pair(parser.tryToParse(it) ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse.deserializationFailure("startDate", it)),
                    parser.tryToParse(endDate) ?: return@get call.respond(HttpStatusCode.BadRequest,
                        ErrorResponse.deserializationFailure("endDate", endDate)))
            } ?: Pair(null, null)
            val absenceData = if (dates.areNulls) {
                getClassAbsence(classID, LocalDate.now().toDateTime(LocalTime.MIDNIGHT))
            } else {
                getClassAbsence(classID, Pair(dates.first!!, dates.second!!))
            }
            val pupilsArray = getPupilsInClass(classID)
            call.respond(AbsenceNoteJSON.encodeToJsonElement(pupilsArray.map { pupil ->
                AbsencePair(pupil, absenceData.filter { it.pupilID == pupil.id }.toSet())
            }))
        }
        post {
            val user = call.authentication.principal<by.enrollie.eversity.security.User>()!!
            val classID =
                (call.parameters["classId"] ?: throw MissingRequestParameterException("classId")).toIntOrNull()
                    ?: throw ParameterConversionException("classId", "classId")
            val placeJobs = AbsenceNoteJSON.decodeFromString<List<PlaceJob>>(call.receiveText())
            kotlin.run {
                for (job in placeJobs) {
                    if (job.date.dayOfWeek == DayOfWeek.SUNDAY.value) return@post call.respond(HttpStatusCode.BadRequest,
                        ErrorResponse.illegalDate(job.date.toString(DATE_FORMAT)))
                }
            }
            val readyAbsence = validateJobsAndConvertToAbsenceData(classID, placeJobs, user.id)
            if (readyAbsence.isFailure) {
                when (val exception = readyAbsence.exceptionOrNull()!!) {
                    is SchoolClassConstraintsViolation -> call.respond(HttpStatusCode.BadRequest,
                        ErrorResponse.schoolClassViolation(exception.violatedConstraints.first,
                            exception.violatedConstraints.second))
                    is NoSuchElementException -> call.respond(HttpStatusCode.BadRequest,
                        ErrorResponse.classNotFound(classID))
                    else -> {
                        LoggerFactory.getLogger(this::class.java)
                            .error("Unknown error at \'post-class-classId-absence\'", exception)
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse.exception(exception))
                    }
                }
            }
            readyAbsence.getOrThrow().let { AbsencePlacer.postAbsence(it.first, it.second) }
            call.respond(HttpStatusCode.OK)
        }
        absenceDate()
    }
}

private fun Route.absenceDate() {
    get("/{date}") {
        val classID = (call.parameters["classId"] ?: throw MissingRequestParameterException("classId")).toIntOrNull()
            ?: throw ParameterConversionException("classId", "classId")
        val date = (DateTimeFormat.forPattern(DATE_FORMAT)
            .tryToParse(call.parameters["date"] ?: throw MissingRequestParameterException("date"))
            ?: throw ParameterConversionException("date", "date")).withTime(LocalTime.MIDNIGHT)

        val absenceData = getClassAbsence(classID, date)
        val pupilsArray = getPupilsInClass(classID)
        call.respond(AbsenceNoteJSON.encodeToJsonElement(pupilsArray.map { pupil ->
            AbsencePair(pupil, absenceData.filter { it.pupilID == pupil.id }.toSet())
        }))
    }
}

private fun Route.pupils() {
    get("/pupils") {
        val classID = (call.parameters["classId"] ?: throw MissingRequestParameterException("classId")).toIntOrNull()
            ?: throw ParameterConversionException("classId", "classId")
        call.respond(getPupilsInClass(classID))
    }
}

private fun Route.timetable() {
    route("/timetable") {
        get {
            val classID =
                (call.parameters["classId"] ?: throw MissingRequestParameterException("classId")).toIntOrNull()
                    ?: throw ParameterConversionException("classId", "classId")
            call.respond(getClassTimetable(classID).toTwoShiftsTimetable(getClass(classID).isSecondShift))
        }
        route("/today") {
            get {
                val classID =
                    (call.parameters["classId"] ?: throw MissingRequestParameterException("classId")).toIntOrNull()
                        ?: throw ParameterConversionException("classId", "classId")
                if (DateTime.now().dayOfWeek == DayOfWeek.SUNDAY.value) return@get call.respond(Json.encodeToJsonElement(
                    null as Lesson?))
                call.respond(Json.encodeToJsonElement(getClassTimetable(classID).toTwoShiftsTimetable(getClass(classID).isSecondShift)[DayOfWeek.of(
                    DateTime.now().dayOfWeek)].toList().first { it.isNotEmpty() }.firstOrNull()))
            }
            get("/current") {
                val classID =
                    (call.parameters["classId"] ?: throw MissingRequestParameterException("classId")).toIntOrNull()
                        ?: throw ParameterConversionException("classId", "classId")
                val currTime = DateTime.now()
                if (currTime.dayOfWeek == DayOfWeek.SUNDAY.value) {
                    return@get call.respond(Json.encodeToJsonElement<Lesson?>(null))
                }
                val lesson = getClassTimetable(classID)[DayOfWeek.of(currTime.dayOfWeek)].find {
                    Interval(DateTime.now()
                        .withTime(it.schedule.startHour.toInt(), it.schedule.startMinute.toInt(), 0, 0),
                        DateTime.now()
                            .withTime(it.schedule.endHour.toInt(), it.schedule.endMinute.toInt(), 0, 0)).contains(
                        currTime)
                }
                call.respond(Json.encodeToJsonElement(mapOf("serverTime" to Json.encodeToJsonElement(DateTime.now()
                    .toString(ISODateTimeFormat.dateTime())), "lesson" to Json.encodeToJsonElement(lesson))))
            }

        }
    }
}

fun Route.classRoute() {
    route("/class/{classId}") {
        authenticate("jwt") {
            classGet()
            classAbsence()
            pupils()
            timetable()
        }
    }
}
