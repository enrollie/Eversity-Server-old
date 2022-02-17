/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.AbsencePlacer
import by.enrollie.eversity.DATE_FORMAT
import by.enrollie.eversity.DefaultDateFormatter
import by.enrollie.eversity.OSO
import by.enrollie.eversity.data_classes.Absence
import by.enrollie.eversity.data_classes.ErrorResponse
import by.enrollie.eversity.data_classes.Lesson
import by.enrollie.eversity.data_classes.Pupil
import by.enrollie.eversity.data_functions.join
import by.enrollie.eversity.data_functions.tryToParse
import by.enrollie.eversity.database.functions.*
import by.enrollie.eversity.exceptions.SchoolClassConstraintsViolation
import by.enrollie.eversity.placer.data_classes.PlaceJob
import by.enrollie.eversity.security.User
import by.enrollie.eversity.uac.OsoSchoolClass
import by.enrollie.eversity.uac.OsoUser
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.joda.time.DateTime
import org.joda.time.Interval
import org.joda.time.LocalTime
import org.joda.time.format.ISODateTimeFormat
import org.slf4j.LoggerFactory
import java.time.DayOfWeek

@Serializable
internal data class ResponseSchoolClass(
    val id: Int,
    val title: String,
    val isSecondShift: Boolean,
    val pupilCount: Int,
    val classTeacherId: Int,
    val classTeacherName: String,
)

private fun Route.classGet() {
    get {
        val user = call.authentication.principal<User>()!!
        val classID = (call.parameters["classId"] ?: throw MissingRequestParameterException("classId")).toIntOrNull()
            ?: throw ParameterConversionException("classId", "classId")
        OSO.authorize(OsoUser(user.user.id, user.user.type), "read", OsoSchoolClass(classID))
        val classData = getClass(classID)
        call.respond(
            ResponseSchoolClass(
                classID,
                classData.title,
                classData.isSecondShift,
                getPupilsInClass(DateTime.now().withTime(LocalTime.MIDNIGHT), classID).size,
                classData.classTeacherID,
                getUserName(classData.classTeacherID).fullForm
            )
        )
    }
}

@Serializable
private data class AbsencePair(val pupil: Pupil, val absences: Set<Absence>)

private fun Route.classAbsence() {
    route("/absence") {
        post {
            val user = call.authentication.principal<User>()!!
            val classID =
                (call.parameters["classId"] ?: throw MissingRequestParameterException("classId")).toIntOrNull()
                    ?: throw ParameterConversionException("classId", "classId")
            OSO.authorize(OsoUser(user.user.id, user.user.type), "post_absence", OsoSchoolClass(classID))
            val placeJobs = call.receive<List<PlaceJob>>()
            kotlin.run { // Validate input
                for (job in placeJobs) {
                    if (job.date.dayOfWeek == DayOfWeek.SUNDAY.value) return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse.illegalDate(job.date.toString(DATE_FORMAT))
                    )
                    val classTimetable =
                        getClassTimetable(classID).toTwoShiftsTimetable(getClass(classID).isSecondShift)[DayOfWeek.of(
                            DateTime.now().dayOfWeek
                        )].join()
                    for (lesson in job.lessonsList) {
                        if (!classTimetable.any { it.place == lesson.toInt() })
                            return@post call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponse.illegalLessonPlace(lesson)
                            )
                    }
                }
            }
            val readyAbsence = validateJobsAndConvertToAbsenceData(classID, placeJobs, user.user.id)
            if (readyAbsence.isFailure) {
                when (val exception = readyAbsence.exceptionOrNull()!!) {
                    is SchoolClassConstraintsViolation -> call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse.schoolClassViolation(
                            exception.violatedConstraints.first,
                            exception.violatedConstraints.second
                        )
                    )
                    is NoSuchElementException -> call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse.classNotFound(classID)
                    )
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
        val user = call.authentication.principal<User>()!!
        val classID = (call.parameters["classId"] ?: throw MissingRequestParameterException("classId")).toIntOrNull()
            ?: throw ParameterConversionException("classId", "classId")
        OSO.authorize(OsoUser(user.user.id, user.user.type), "read_absence", OsoSchoolClass(classID))
        val date = (DefaultDateFormatter
            .tryToParse(call.parameters["date"] ?: throw MissingRequestParameterException("date"))
            ?: throw ParameterConversionException("date", "date")).withTime(LocalTime.MIDNIGHT)
        val absenceData = getClassAbsence(classID, date)
        val pupilsArray = getPupilsInClass(date, classID)
        call.respond(pupilsArray.map { pupil ->
            AbsencePair(pupil, absenceData.filter { it.pupilID == pupil.id }.toSet())
        })
    }
}

private fun Route.pupils() {
    get("/pupils") {
        val user = call.authentication.principal<User>()!!
        val classID = (call.parameters["classId"] ?: throw MissingRequestParameterException("classId")).toIntOrNull()
            ?: throw ParameterConversionException("classId", "classId")
        OSO.authorize(OsoUser(user.user.id, user.user.type), "read_members", OsoSchoolClass(classID))
        val date = call.request.queryParameters["date"]?.let {
            DefaultDateFormatter.tryToParse(it) ?: throw ParameterConversionException("date", "date")
        } ?: DateTime.now().withTime(LocalTime.MIDNIGHT)
        call.respond(getPupilsInClass(date, classID))
    }
}

private fun Route.timetable() {
    route("/timetable") {
        get {
            val user = call.authentication.principal<User>()!!
            val classID =
                (call.parameters["classId"] ?: throw MissingRequestParameterException("classId")).toIntOrNull()
                    ?: throw ParameterConversionException("classId", "classId")
            OSO.authorize(OsoUser(user.user.id, user.user.type), "read", OsoSchoolClass(classID))
            call.respond(getClassTimetable(classID).toTwoShiftsTimetable(getClass(classID).isSecondShift))
        }
        route("/today") {
            get {
                val user = call.authentication.principal<User>()!!
                val classID =
                    (call.parameters["classId"] ?: throw MissingRequestParameterException("classId")).toIntOrNull()
                        ?: throw ParameterConversionException("classId", "classId")
                OSO.authorize(OsoUser(user.user.id, user.user.type), "read", OsoSchoolClass(classID))
                if (DateTime.now().dayOfWeek == DayOfWeek.SUNDAY.value) return@get call.respond(
                    Json.encodeToJsonElement(
                        null as Lesson?
                    )
                )
                call.respond(
                    Json.encodeToJsonElement(
                        getClassTimetable(classID).toTwoShiftsTimetable(getClass(classID).isSecondShift)[DayOfWeek.of(
                            DateTime.now().dayOfWeek
                        )]
                    )
                )
            }
            get("/current") {
                val user = call.authentication.principal<User>()!!
                val classID =
                    (call.parameters["classId"] ?: throw MissingRequestParameterException("classId")).toIntOrNull()
                        ?: throw ParameterConversionException("classId", "classId")
                OSO.authorize(OsoUser(user.user.id, user.user.type), "read", OsoSchoolClass(classID))
                val currTime = DateTime.now()
                if (currTime.dayOfWeek == DayOfWeek.SUNDAY.value) {
                    return@get call.respond(Json.encodeToJsonElement<Lesson?>(null))
                }
                val lesson = getClassTimetable(classID)[DayOfWeek.of(currTime.dayOfWeek)].find {
                    Interval(
                        DateTime.now()
                            .withTime(it.schedule.startHour.toInt(), it.schedule.startMinute.toInt(), 0, 0),
                        DateTime.now()
                            .withTime(it.schedule.endHour.toInt(), it.schedule.endMinute.toInt(), 0, 0)
                    ).contains(
                        currTime
                    )
                }
                call.respond(
                    Json.encodeToJsonElement(
                        mapOf(
                            "serverTime" to Json.encodeToJsonElement(
                                DateTime.now()
                                    .toString(ISODateTimeFormat.dateTime())
                            ), "lesson" to Json.encodeToJsonElement(lesson)
                        )
                    )
                )
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
