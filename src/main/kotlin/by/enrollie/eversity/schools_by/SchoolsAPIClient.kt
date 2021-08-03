/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.schools_by

import by.enrollie.eversity.data_classes.APIUserType
import by.enrollie.eversity.data_classes.Pupil
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.slf4j.Logger
import java.text.SimpleDateFormat
import java.util.*
import javax.naming.AuthenticationException

/**
 * Wrapper of "Schools.by" API, using KTor
 *
 * @author Pavel Matusevich (Neitex)
 * @constructor Constructs wrapper class without initializing token.
 */
@Suppress("unused")
class SchoolsAPIClient() {
    private var token: String = "null"
    private var userType: APIUserType? = null

    private val logger: Logger = org.slf4j.LoggerFactory.getLogger("Schools.by")

    private var client = HttpClient {
        expectSuccess = false
        defaultRequest {
            headers.append("Authorization", "Token $token")
            timeout {
                this.connectTimeoutMillis = 1000
                this.requestTimeoutMillis = 1000
                this.socketTimeoutMillis = 2000
            }
        }
    }

    /**
     * Constructs "Schools.by" API wrapper with predefined API token
     *
     * @property token Schools.by API token string
     *
     */
    @Suppress("unused")
    constructor(Token: String) : this() {
        token = Token
    }

    /**
     * Constructs "Schools.by" API wrapper with predefined API token and user type
     *
     * @property token Schools.by API token string
     * @property UserType Type of user
     *
     */
    @Suppress("unused")
    constructor(Token: String, UserType: APIUserType) : this() {
        token = Token
        userType = UserType
    }


    /**
     * Gets "Schools.by" API token by username and password
     *
     * @param username Schools.by username
     * @param password Schools.by password
     *
     * @return Schools.by token, else null if password is wrong
     */
    suspend fun getAPIToken(username: String, password: String): String? {
        val tempClient = HttpClient {
            expectSuccess = false
        }
        val credentialsJSON = JsonObject(
            mapOf(
                "username" to Json.parseToJsonElement("\"$username\""),
                "password" to Json.parseToJsonElement("\"$password\"")
            )
        )
        val resp = tempClient.use {
            it.request<HttpResponse> {
                method = HttpMethod.Post
                this.contentType(ContentType.Application.Json)
                this.url.takeFrom("https://schools.by/api/auth")
                this.body = credentialsJSON.toString()
            }
        }
        if (resp.status != HttpStatusCode.OK)
            return null
        val response = Json.parseToJsonElement(String(resp.readBytes())).jsonObject
        return if (response.containsKey("token")) {
            token = response.getValue("token").jsonPrimitive.content
            token
        } else {
            null
        }
    }

    /**
     *Gets current user data (current user defined by token)
     *
     * @return User data [JsonObject]
     * @throws IllegalArgumentException If token is not initialized
     * @throws AuthenticationException Thrown if Schools.by did not accept token
     * @throws UnknownError Thrown if something odd happens on side of Schools.by
     */
    suspend fun getCurrentUserData(): JsonObject {
        if (token == "null") {
            throw IllegalStateException("Token is not initialized! Get it either by using getAPIToken() or by constructing class with it")
        }
        val response = client.request<HttpResponse> {
            method = HttpMethod.Get
            this.url.takeFrom("https://schools.by/subdomain-api/user/current")
        }
        when (response.status) {
            HttpStatusCode.OK -> {
                val resp = response.receive<String>()
                return Json.parseToJsonElement(resp).jsonObject
            }
            HttpStatusCode.Unauthorized -> {
                throw AuthenticationException("Schools.by did not accept token. Is it correct?")
            }
            HttpStatusCode.InternalServerError -> {
                throw UnknownError("Schools.by returned HTTP Code 500 (Internal Server Error)")
            }
            else -> {
                throw UnknownError("Schools.by returned HTTP Code ${response.status.value} (${response.status.description})")
            }
        }
    }

    /**
     * Gets day summary for pupil (identified by User ID).
     * @return [JsonObject] containing timetable of given User ID for given date
     *
     * @param userID ID of pupil to get
     * @param date Defaults to current date. Date should be presented in "YYYY-MM-DD" form
     *
     * @throws AuthenticationException Thrown, if Schools.by refuses access (usually, when you don't have permission to get this pupil's info)
     * @throws IllegalStateException Thrown, if token is not initialized
     */

    suspend fun getSummaryForDay(
        userID: String,
        date: String = SimpleDateFormat("YYYY-MM-dd").format(Calendar.getInstance().time)
    ): JsonObject {
        if (token == "null") {
            throw IllegalStateException("Token is not initialized! Get it either by using getAPIToken() or by constructing class with it")
        }
        val response = client.request<HttpResponse> {
            method = HttpMethod.Get
            url.takeFrom("https://schools.by/subdomain-api/pupil/$userID/daybook/day/$date")
        }
        when (response.status) {
            HttpStatusCode.OK -> {
                val responseString = response.receive<String>()
                return Json.parseToJsonElement(responseString).jsonObject
            }
            HttpStatusCode.Unauthorized -> {
                logger.debug("Schools.by API returned HTTP Code 500 (Unauthorized)")
                throw AuthenticationException("Schools.by did not accept this token. Are you sure you have permission to access this pupil?")
            }
            HttpStatusCode.InternalServerError -> {
                logger.error("Schools.by API returned HTTP Code 500 (Internal Server Error)")
                throw UnknownError("Schools.by returned HTTP Code 500 (Internal Server Error)")
            }
            else -> {
                logger.error("Schools.by API returned unknown HTTP code (${response.status.value} (${response.status.description}))")
                logger.debug("Returned message: ${response.receive<String>()}")
                throw UnknownError("Schools.by returned HTTP Code ${response.status.value} (${response.status.description})")
            }
        }
    }

    /**
     * Returns information about pupil with given ID
     *
     * @return [JsonObject] containing information about pupil
     *
     * @param userID ID of pupil
     *
     * @throws AuthenticationException Thrown, if Schools.by refuses access (usually, when you don't have permission to get this pupil's info)
     * @throws IllegalStateException Thrown, if token is not initialized
     */
    suspend fun getPupilInfo(userID: String): JsonObject {
        if (token == "null") {
            throw IllegalStateException("Token is not initialized! Get it either by using getAPIToken() or by constructing class with it")
        }
        val response = client.request<HttpResponse> {
            method = HttpMethod.Get
            url.takeFrom("https://schools.by/subdomain-api/pupil/$userID/info")
        }
        when (response.status) {
            HttpStatusCode.OK -> {
                val responseString = response.receive<String>()
                return Json.parseToJsonElement(responseString).jsonObject
            }
            HttpStatusCode.Unauthorized -> {
                throw AuthenticationException("Schools.by did not accept this token. Are you sure you have permission to access this pupil?")
            }
            HttpStatusCode.InternalServerError -> {
                throw UnknownError("Schools.by returned HTTP Code 500 (Internal Server Error)")
            }
            else -> {
                throw UnknownError("Schools.by returned HTTP Code ${response.status.value} (${response.status.description})")
            }
        }
    }

    /**
     * Returns information about current quarter. Does not require token.
     *
     * @return [JsonObject] containing current quarter info
     *
     * @throws UnknownError Thrown, when Schools.by returned non-200 HTTP code
     */
    suspend fun getCurrentQuarter(): JsonObject {
        val response = client.request<HttpResponse> {
            method = HttpMethod.Get
            headers.clear()
            url.takeFrom("https://schools.by/subdomain-api/quarter/current")
        }
        when (response.status) {
            HttpStatusCode.OK -> {
                val responseString = response.receive<String>()
                return Json.parseToJsonElement(responseString).jsonObject
            }
            else -> throw UnknownError("Schools.by returned HTTP code ${response.status.value} (${response.status.description})")
        }
    }

    /**
     * Fetches parent's pupils. As Schools.by did not bother with security, it does not require authorization (token)
     * @param parentID ID of parent
     * @return List of given parent's pupils
     * @throws UnknownError Thrown, if Schools.by returned non-200 HTTP code
     */
    suspend fun fetchParentsPupils(parentID: Int): List<Pupil> {
        val response = HttpClient().use {
            it.request<HttpResponse> {
                method = HttpMethod.Get
                url.takeFrom("https://schools.by/subdomain-api/parent/$parentID/pupils")
            }
        }
        when (response.status) {
            HttpStatusCode.OK -> {
                val responseString = response.receive<String>()
                val responseJSON = Json.parseToJsonElement(responseString).jsonArray
                return responseJSON.map {
                    val pupilData = it.jsonObject
                    Pupil(
                        pupilData["id"]?.jsonPrimitive?.int!!,
                        pupilData["first_name"]?.jsonPrimitive?.content.toString(),
                        pupilData["last_name"]?.jsonPrimitive?.content.toString(),
                        pupilData["class_id"]?.jsonPrimitive?.int!!
                    )
                }
            }
            else -> {
                throw UnknownError("Schools.by returned HTTP code ${response.status.value} (${response.status.description})")
            }
        }
    }
}