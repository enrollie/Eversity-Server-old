/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity

import by.enrollie.eversity.data_classes.DayOfWeek
import by.enrollie.eversity.data_classes.Pupil
import by.enrollie.eversity.data_classes.TeacherLesson
import by.enrollie.eversity.data_classes.TimetableDay
import by.enrollie.eversity.schools_by.SchoolsWebWrapper
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class SchoolsWebWrapperTests {
    private val testDataBasePath = "src/test/kotlin/by/enrollie/eversity/test_data/schoolsWeb"
    private val validateCookiesData = Pair("csrftoken", "sessionid")

    private val client = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                when {
                    request.url.fullPath.contains(
                        "/class/1" //test data for class timetable parser
                    ) -> {
                        val file = File("$testDataBasePath/class_timetable.html")
                        if (!file.exists()) {
                            throw IllegalArgumentException("Class timetable sample data is not found (path: \"${file.absolutePath}\")")
                        }
                        respond(file.readBytes())
                    }
                    request.url.fullPath.contains(
                        "class/2" //test data for class teacher parser
                    ) -> {
                        val file = File("$testDataBasePath/class_page.html")
                        if (!file.exists()) {
                            throw IllegalArgumentException("Class page sample data is not found (path: \"${file.absolutePath}\")")
                        }
                        respond(file.readBytes())
                    }
                    request.url.fullPath.contains(
                        "teacher/1"
                    ) -> {
                        val file = File("$testDataBasePath/teacher_timetable.html")
                        if (!file.exists()) {
                            throw IllegalArgumentException("Teacher timetable sample data is not found (path: \"${file.absolutePath}\")")
                        }
                        respond(file.readBytes())
                    }
                    request.url.fullPath.contains("class/3") -> {
                        val file = File("$testDataBasePath/pupils_list.html")
                        if (!file.exists()) {
                            throw IllegalArgumentException("Pupils list sample data is not found (path: \"${file.absolutePath}\")")
                        }
                        respond(file.readBytes())
                    }
                    else ->
                        error("Unhandled ${request.url.fullPath}")

                }
            }
        }
    }

    @Test
    fun testClassTeacherParser() {
        runBlocking {
            val response = SchoolsWebWrapper(client).fetchClassForTeacher(2)
            assertEquals(652900, response)
        }
    }

    @Test
    fun testClassTimetableParser() {
        runBlocking {
            val validResponseFile = File("$testDataBasePath/class_timetable_valid.json")
            if (!validResponseFile.exists()) {
                throw IllegalArgumentException("Valid result for class timetable is not found (path: \"${validResponseFile.absolutePath}\")")
            }
            val validMap = Json.decodeFromString<Map<DayOfWeek, TimetableDay>>(String(validResponseFile.readBytes()))
            val responseMap = SchoolsWebWrapper(client).fetchClassTimetable(1)
            assertEquals(
                validMap,
                responseMap,
                "Response map size: ${responseMap.size}; Valid map size: ${validMap.size}"
            )
        }
    }

    @Test
    fun testTeacherTimetableParser() {
        runBlocking {
            val validResponseFile = File("$testDataBasePath/teacher_timetable_valid.json")
            if (!validResponseFile.exists()) {
                throw IllegalArgumentException("Valid result for teacher timetable is not found (path: \"${validResponseFile.absolutePath}\")")
            }
            val validMap =
                Json.decodeFromString<Map<DayOfWeek, Array<TeacherLesson>>>(String(validResponseFile.readBytes()))
            val responseMap = SchoolsWebWrapper(client).fetchTeacherTimetable(1)
            assertEquals(Json.encodeToString(validMap), Json.encodeToString(responseMap))
        }
    }

    @Test
    fun testPupilListParser() {
        runBlocking {
            val validResponseFile = File("$testDataBasePath/pupils_list_valid.json")
            if (!validResponseFile.exists()) {
                throw IllegalArgumentException("Valid result for pupils list is not found (path: \"${validResponseFile.absolutePath}\")")
            }
            val validMap = Json.decodeFromString<Array<Pupil>>(String(validResponseFile.readBytes()))
            val responseMap = SchoolsWebWrapper(client).fetchPupilsArray(3)
            assertEquals(Json.encodeToString(validMap), Json.encodeToString(responseMap))
        }
    }
}