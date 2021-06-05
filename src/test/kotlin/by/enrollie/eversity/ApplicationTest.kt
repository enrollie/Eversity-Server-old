/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity

import by.enrollie.eversity.data_classes.DayOfWeek
import by.enrollie.eversity.data_classes.TimetableDay
import by.enrollie.eversity.routes.registerPupilsRouting
import by.enrollie.eversity.routes.registerAuthRoute
import by.enrollie.eversity.schools_by.SchoolsWebWrapper
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.sql.Time
import kotlin.test.Test
import kotlin.test.*
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun testRoot() {
        withTestApplication({
            registerPupilsRouting()
            registerAuthRoute()
        }) {
            with(handleRequest(HttpMethod.Post, "/api/login") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(Json.parseToJsonElement("{\"username\":\"123456789\",\"password\":\"123\"}").toString())
            }) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
            with(handleRequest(HttpMethod.Post, "/api/login") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    Json.parseToJsonElement("{\"username\":\"ENROLLIE\",\"password\":\"TEST_CREDENTIALS_PLEASE_DONT_REMOVE_CONTACT_AT_GITHUB\"}")
                        .toString()
                )
            }) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }




}