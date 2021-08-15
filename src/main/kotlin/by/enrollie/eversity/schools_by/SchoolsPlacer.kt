/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.schools_by

import by.enrollie.eversity.data_classes.Mark
import by.enrollie.eversity.data_classes.Pupil
import by.enrollie.eversity.exceptions.MarkAlreadyExistsException
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.Logger
import java.text.SimpleDateFormat
import java.util.*

class SchoolsPlacer : SchoolsWebWrapper {

    companion object {
        lateinit var logger: Logger
            private set

        fun initialize(mainLogger: Logger) {
            synchronized(this) {
                logger = mainLogger
            }
        }
    }

    /**
     * Constructs wrapper with given cookies and standard subdomain
     * @param Cookies Cookies
     * @constructor Constructs wrapper with given cookies
     */
    constructor(Cookies: Pair<String, String>) : super(Cookies)

    /**
     * To be used only for testing. Sets default HttpClient to given one
     * @param httpClient Mock http client
     */
    constructor(httpClient: HttpClient) : super(httpClient)

    /**
     * Posts mark to Schools.by
     * @param pupil Pupil, who's mark needs to be placed
     * @param journalID ID of journal
     * @param lessonID ID of lesson to post mark
     * @param date Date of lesson (defaults to current day)
     * @param mark Mark to be put (defaults to -1, which stands for "absent" in Schools.by)
     * @param markID ID of existing mark
     * @throws MarkAlreadyExistsException Thrown, if mark in given lesson on given date does already exist
     * @throws UnknownError Thrown, if something gone wrong on Schools.by side
     */
    private suspend fun placeMark(
        pupil: Pupil,
        lessonID: Int,
        journalID: Int,
        lessonPlace: Short,
        date: String = SimpleDateFormat("YYYY-MM-dd").format(Calendar.getInstance().time),
        mark: Short? = -1,
        markID: Int? = null
    ): Mark {
        val journalString = "${subdomainURL}marks/class-subject:$journalID/set"
        val markResponse = client.submitForm<HttpResponse>(formParameters = Parameters.build {
            append("id", markID?.toString() ?: "")
            append("m", mark?.toString() ?: "")
            append("note", "")
            append("lesson_id", lessonID.toString())
            append("lesson_date", date)
            append("pupil_id", pupil.id.toString())
        }) {
            url.takeFrom(journalString)
        }
        val responseBody = markResponse.receive<String>()
        when (markResponse.status) {
            HttpStatusCode.OK, HttpStatusCode.Found -> {
                val responseMarkID =
                    Json.parseToJsonElement(responseBody).jsonObject["id"]?.jsonPrimitive?.content?.toInt() ?: 0
                return Mark(responseMarkID, mark, lessonPlace, pupil)
            }
            HttpStatusCode.BadRequest -> {
                throw MarkAlreadyExistsException("Mark already exists. Journal ID: $journalID; Lesson ID: $lessonID;")
            }
            HttpStatusCode.InternalServerError -> {
                throw UnknownError("Schools.by returned Http Code 500 (Bad request); Access url: $journalString")
            }
            else -> {
                throw UnknownError("Schools.by returned unknown HTTP code ${markResponse.status.value} (${markResponse.status.description})")
            }
        }
    }

    /**
     * Sends absence data to Schools.by. Valid cookies are required for this action
     * @param pupil Pupil, who's absence is sending
     * @param absenceList List of lessons numbers to place and absence (List( Pair(Lesson number, Is absent) ))
     * @param tokenAPI Token for Schools.by API
     * @return List of Pair(Failed lessons count, List of failed lessons (null if none))
     */
    suspend fun placeAbsence(
        pupil: Pupil,
        absenceList: List<Pair<Short, Boolean>>,
        tokenAPI: String,
        date: String = SimpleDateFormat("YYYY-MM-dd").format(Calendar.getInstance().time)
    ): Pair<Int, List<Short>?> {
        return Pair(absenceList.size, absenceList.map { it.first }) //Unfortunately, Schools.by killed their API,
                                                                    //so currently sending absence data to them is not possible
        /*
        val pupilTimetable = try {
            JsonObject(mapOf())
//            SchoolsAPIClient(tokenAPI, APIUserType.Teacher).getSummaryForDay(pupil.id.toString(), date)
        } catch (e: UnknownError) {
            return Pair(absenceList.size, absenceList.map { it.first })
        }
        val marksList = mutableSetOf<Pair<Mark, Pair<Int, Int>>>() //Pair: (Mark, Pair(journal ID, lesson ID))
        val errorList = mutableListOf<Short>()
        for (absence in absenceList) {
            try {
                val lesson = pupilTimetable["lessons"]?.jsonObject?.get(absence.first.toString())?.jsonObject
                    ?: throw NoSuchElementException("Lesson with place ${absence.first} was not found")
                val lessonID = lesson["id"]?.jsonPrimitive?.intOrNull
                    ?: throw NoSuchElementException("Lesson ID for lesson #${absence.first} was not found")
                val journalID = lesson["class_subject"]?.jsonPrimitive?.intOrNull
                    ?: throw NoSuchElementException("Journal ID for lesson #${absence.first} was not found")
                val existingMarkData = try {
                    lesson["mark_data"]?.jsonObject
                } catch (e: IllegalArgumentException) {
                    null
                }
                val existingMark = if (existingMarkData != null) {
                    val markID =
                        existingMarkData["id"]?.jsonPrimitive?.intOrNull ?: throw UnknownError("Mark ID is null")
                    val markValueStr =
                        existingMarkData["m"]?.jsonPrimitive?.content ?: throw UnknownError("Mark value is null")
                    val markValue: Short? = when (markValueStr) {
                        "", "з.", "н/з", " " -> null
                        "н" -> -1
                        else -> markValueStr.toShortOrNull()
                    }
                    Mark(markID, markValue, absence.first, pupil)
                } else null
                try {
                    val placedMark = if (existingMark != null) {
                        if (existingMark.markNum == null || existingMark.markNum == (-1).toShort()) {
                            placeMark(
                                pupil,
                                lessonID,
                                journalID,
                                lessonPlace = absence.first,
                                mark = if (absence.second) -1 else null,
                                date = date,
                                markID = existingMark.id
                            )
                        } else existingMark
                    } else placeMark(
                        pupil,
                        lessonID,
                        journalID,
                        lessonPlace = absence.first,
                        mark = if (absence.second) -1 else null,
                        date = date
                    )
                    marksList.add(Pair(placedMark, Pair(journalID, lessonID)))
                } catch (e: MarkAlreadyExistsException) {
                    logger.error(e)
                    continue
                } catch (e: UnknownError) {
                    logger.error(e)
                    continue
                }
            } catch (e: IllegalArgumentException) {
                logger.warn(e.message)
                e.printStackTrace()
                errorList.add(absence.first)
                continue
            } catch (e: NoSuchElementException) {
                logger.warn(e.message)
                logger.debug("Json: ${Json.encodeToString(pupilTimetable)}")
                errorList.add(absence.first)
                continue
            }
        }
        batchInsertMarks(marksList.toList())
        return if (errorList.isEmpty()) Pair(0, null) else Pair(errorList.size, errorList)
         */
    }
}