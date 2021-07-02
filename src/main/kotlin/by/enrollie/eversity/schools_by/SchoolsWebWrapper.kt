/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.schools_by

import by.enrollie.eversity.configSubdomainURL
import by.enrollie.eversity.data_classes.*
import by.enrollie.eversity.data_functions.russianDayNameToDayOfWeek
import by.enrollie.eversity.data_functions.toTimeConstraint
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.util.*
import it.skrape.core.htmlDocument
import it.skrape.exceptions.ElementNotFoundException
import it.skrape.selects.forEachLink
import it.skrape.selects.html5.*
import org.slf4j.Logger


/**
 * A wrapper for parsing and making data out of Schools.by HTML pages
 * @author Pavel Matusevich
 */
open class SchoolsWebWrapper {
    private val userAgent = "Eversity/1.0 (Windows NT 10.0; Win32; x86; rv:1.0) KTor/1.5.4 Eversity/1.0"

    protected var subdomainURL: String = "https://demo.schools.by/"
    private var schoolsBYCookies: Pair<String, String> = Pair("", "")

    protected val logger: Logger = org.slf4j.LoggerFactory.getLogger("Schools.by")

    protected var client = HttpClient {
        followRedirects = true
        expectSuccess = false
        defaultRequest {
            cookie("csrftoken", schoolsBYCookies.first)
            cookie("sessionid", schoolsBYCookies.second)
            userAgent(userAgent)
            timeout {
                this.connectTimeoutMillis = 1000
                this.requestTimeoutMillis = 1000
                this.socketTimeoutMillis = 2000
            }
        }
    }

    /**
     * Constructs wrapper with default settings. You need to login before using other functions,
     * as they require subdomains, cookies, etc.
     * @constructor Constructs wrapper class without initializing cookies and subdomain
     */
    constructor() {
        subdomainURL = configSubdomainURL ?: "https://demo.schools.by/"
        schoolsBYCookies = Pair("", "")
    }

    /**
     * Constructs wrapper with given subdomain. You need to login before using other functions,
     * as they require cookies.
     * @param SubdomainURL URL for school's Schools.by page ('/' on the end is required)
     * @constructor Constructs wrapper with given subdomain
     */
    constructor(SubdomainURL: String) {
        schoolsBYCookies = Pair("", "")
        subdomainURL = SubdomainURL
    }

    /**
     * Constructs wrapper with given cookies and subdomain. Wrapper is ready to use.
     * @constructor Constructs wrapper with given cookies and subdomain URL
     * @param SubdomainURL URL for school's Schools.by page ('/' on the end is required)
     * @param Cookies Cookies of logged in user
     */
    constructor(SubdomainURL: String, Cookies: Pair<String, String>) {
        schoolsBYCookies = Cookies
        subdomainURL = SubdomainURL
    }

    /**
     * Constructs wrapper with given cookies and standard subdomain
     * @param Cookies Cookies
     * @constructor Constructs wrapper with given cookies
     */
    constructor(Cookies: Pair<String, String>) {
        subdomainURL = configSubdomainURL ?: "https://demo.schools.by/"
        schoolsBYCookies = Cookies
    }

    /**
     * To be used only for testing. Sets default HttpClient to given one
     * @param httpClient Mock http client
     */
    constructor(httpClient: HttpClient) {
        client = httpClient
    }

    /**
     * Gets Schools.by login cookies and subdomain page.
     * Asserts, that login data is correct. To check it, refer to [SchoolsAPIClient.getAPIToken].
     *
     * @param username Schools.by username
     * @param password Schools.by password
     *
     * @return Pair of cookies (csrftoken, sessionid)
     *
     * @throws UnknownError Thrown, if something goes wrong (i.e. Schools.by returned non-200 HTTP code)
     * @throws NoSuchElementException Thrown, if any of cookies is not found
     */
    suspend fun login(username: String, password: String): Pair<String, String> {
        val baseCSRFresponse = HttpClient().use {
            it.get<HttpResponse>("https://schools.by/login") {
                headers.append(HttpHeaders.UserAgent, userAgent)
                userAgent(userAgent)
            }
        }
        var csrfTokenCookie = baseCSRFresponse.setCookie().find { it.name == "csrftoken" }
            ?: throw NoSuchElementException("CSRFToken not found after baseCSRFresponse")
        var csrfToken = csrfTokenCookie.value
        val finalCSRFresponse: HttpResponse =
            HttpClient {
                followRedirects = false
                expectSuccess = false
            }.use {
                it.submitForm(
                    url = "https://schools.by/login",
                    formParameters = Parameters.build {
                        append("csrfmiddlewaretoken", csrfToken)
                        append("username", username)
                        append("password", password)
                    }
                ) {
                    cookie("csrftoken", csrfToken)
                    cookie("slc_cookie", "%7BslcMakeBetter%7D")
                    header(HttpHeaders.Referrer, subdomainURL)
                    header(HttpHeaders.UserAgent, userAgent)
                }
            }
        csrfTokenCookie = finalCSRFresponse.setCookie().find { it.name == "csrftoken" } ?: throw NoSuchElementException(
            "CSRFToken not found after finalCSRFresponse"
        )
        csrfToken = csrfTokenCookie.value
        val sessionidCookie = finalCSRFresponse.setCookie().find { it.name == "sessionid" }
            ?: throw NoSuchElementException("SessionID not found after finalCSRFresponse")
        val sessionid = sessionidCookie.value
        schoolsBYCookies = Pair(csrfToken, sessionid)
        client = client.config {
            defaultRequest {
                cookie("csrftoken", schoolsBYCookies.first)
                cookie("sessionid", schoolsBYCookies.second)
            }
        }
        return schoolsBYCookies
    }

    /**
     * Checks if given cookies are valid (if no cookies given, checks internal set of cookies)
     * If given cookies are valid AND [changeInternal] is true -> changes internal set of cookies to given one
     *
     * @param cookies Set of cookies (csrftoken, sessionid). If null - checks internal set of cookies
     * @param changeInternal Default: true. Indicates whether to change internal cookies to given or not
     *
     * @return true if cookies are valid, false otherwise
     */
    suspend fun validateCookies(cookies: Pair<String, String>? = null, changeInternal: Boolean = true): Boolean {
        val response = HttpClient().use {
            it.request<HttpResponse> {
                url.takeFrom(subdomainURL)
                method = HttpMethod.Get
                cookie("csrftoken", cookies?.first ?: schoolsBYCookies.first)
                cookie("sessionid", cookies?.second ?: schoolsBYCookies.second)
            }
        }

        try {
            val html: String = response.receive()
            htmlDocument(html) {
                form {
                    withAttribute = Pair("action", "https://schools.by/login")
                    findAll {
                        //If found, cookies are not valid. Will return false at end.
                    }
                }
            }
        } catch (e: NoTransformationFoundException) {
            logger.error(e)
            return false //Did not recieve page correctly.
        } catch (e: ElementNotFoundException) {
            if (changeInternal && cookies != null) {
                schoolsBYCookies = cookies
                client = client.config {
                    defaultRequest {
                        cookie("csrftoken", schoolsBYCookies.first)
                        cookie("sessionid", schoolsBYCookies.second)
                    }
                }
            }
            return true //Login form not found => we are logged in.
        }
        return false //login form found (no exception were thrown)
    }

    /**
     * Returns unsorted array of [Pupil] for given class.
     *
     * @param classID ID of needed class
     *
     * @return [Array] of [Pupil] from given [classID]
     * @throws IllegalArgumentException Thrown, if no pupils found.
     */
    suspend fun fetchPupilsArray(classID: Int): Array<Pupil> {
        val response = client.request<HttpResponse> {
            url.takeFrom("${subdomainURL}class/$classID/pupils")
            method = HttpMethod.Get
        }
        val pageString = response.receive<String>()
        var pupilsArray = arrayOf<Pupil>()
        var pupilsCounter = 1
        try {
            htmlDocument(pageString) {
                div {
                    withClass = "pupil"
                    findAll {
                        a {
                            withClass = "user_type_1"
                            findAll {
                                forEach {
                                    pupilsArray += Pupil(
                                        it.attribute("href").removePrefix("/pupil/").toInt(),
                                        it.text.split(' ')[1],
                                        it.text.split(' ')[0],
                                        classID
                                    )
                                    pupilsCounter++
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: ElementNotFoundException) {
            logger.error(e)
            throw IllegalArgumentException()
        }
        return pupilsArray
    }

    /**
     *
     *
     * @param classID ID of needed class
     *
     * @return [Array] of [Pupil] from given [classID]
     * @throws IllegalArgumentException Thrown, if no class teacher is not found.
     */
    suspend fun fetchClassForTeacher(classID: Int): Int {
        val response = client.request<HttpResponse> {
            url.takeFrom("$subdomainURL/class/$classID")
            method = HttpMethod.Get
        }
        val pageString = response.receive<String>()
        var classTeacherID: Int = -1

        try {
            htmlDocument(pageString) {
                div {
                    withClass = "r_user_info"
                    p {
                        withClass = "name"
                        a {
                            findFirst {
                                classTeacherID =
                                    this.attribute("href").replaceBefore("/teacher/", "").removePrefix("/teacher/")
                                        .toInt()
                            }
                        }
                    }
                }
            }
        } catch (e: ElementNotFoundException) {
            logger.debug(e.message)
            throw IllegalArgumentException()
        }
        return classTeacherID
    }

    /**
     * Fetches class timetable
     *
     * @param classID ID of class
     * @return Map <[DayOfWeek], Array of [Lesson]>, containing all work days (from Monday to Saturday)
     * @throws NotFoundException Thrown, if Schools.by returned 404 code (usually, class ID is invalid)
     */
    suspend fun fetchClassTimetable(classID: Int): Map<DayOfWeek, Array<Lesson>> {
        val response = client.request<HttpResponse> {
            url.takeFrom("$subdomainURL/class/$classID/timetable")
            method = HttpMethod.Get
            expectSuccess = false
        }
        if (response.status == HttpStatusCode.NotFound) {
            throw NotFoundException("Timetable of class with class ID $classID not found.")
        }
        val pageString = response.receive<String>()
        val timetableMap = mutableMapOf<DayOfWeek, Array<Lesson>>()
        try {
            htmlDocument(pageString) {
                div {
                    withClass = "ttb_boxes"
                    div {
                        withClass = "ttb_box"
                        findAll {
                            forEachIndexed { _, mainDiv ->
                                val day =
                                    russianDayNameToDayOfWeek(mainDiv.div {
                                        withClass = "ttb_day"; findFirst { ownText }
                                    })
                                var dayTimetable = arrayOf<Lesson>()
                                mainDiv.tbody {
                                    tr {
                                        findAll {
                                            forEachIndexed { _, doc ->
                                                var add = true
                                                val num =
                                                    doc.td { withClass = "num"; findFirst { ownText } }.dropLast(1)
                                                        .toInt()
                                                val time =
                                                    doc.td { withClass = "time"; findFirst { ownText } }
                                                        .toTimeConstraint()
                                                        ?: TimeConstraints(0, 0, 0, 0)
                                                val name = doc.td {
                                                    val titles = mutableListOf<String>()
                                                    withClass = "subjs"; findFirst {
                                                    try {
                                                        a {
                                                            findAll {
                                                                forEach { doc ->
                                                                    if (!titles.contains(doc.attribute("title"))
                                                                        && doc.attribute("title").isNotEmpty()
                                                                    )
                                                                        titles.add(doc.attribute("title"))
                                                                }
                                                            }
                                                        }
                                                    } catch (e: ElementNotFoundException) {
                                                        try {
                                                            span {
                                                                findAll {
                                                                    forEach { doc ->
                                                                        if (!titles.contains(doc.attribute("title"))
                                                                            && doc.attribute("title").isNotEmpty()
                                                                        )
                                                                            titles.add(doc.attribute("title"))
                                                                    }
                                                                }
                                                            }
                                                        } catch (e: ElementNotFoundException) {
                                                            add = false
                                                        }
                                                    }
                                                }

                                                    return@td titles.toSet().joinToString(" / ")
                                                }
                                                if (add)
                                                    dayTimetable += Lesson(num, name, time)
                                            }
                                        }
                                    }
                                }
                                timetableMap[day] = dayTimetable
                            }
                        }
                    }
                }
            }
        } catch (e: ElementNotFoundException) {
            logger.error(e)
        }
        return timetableMap
    }

    /**
     * Gets class string for current teacher.
     * @return String, containing URL to class
     */
    suspend fun fetchClassForCurrentUser(): Int? {
        val response = HttpClient {
            followRedirects = true
        }.use {
            it.request<HttpResponse> {
                cookie("csrftoken", schoolsBYCookies.first)
                cookie("sessionid", schoolsBYCookies.second)
                url.takeFrom("https://schools.by/login")
                method = HttpMethod.Get
            }
        }

        val pageString = response.receive<String>()
        var classID: Int? = null
        try {
            htmlDocument(pageString) {
                div {
                    withClass = "pp_line"
                    findAll {
                        a {
                            withAttributeKey = "href"
                            findAll {
                                forEachLink { _, url ->
                                    if (url.contains(".schools.by/class/")) {
                                        classID = url.replaceBeforeLast('/', "").drop(1).toInt()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: ElementNotFoundException) {
            logger.error(e)
            return null
        }
        return classID
    }

    /**
     * Fetches teacher timetable
     * @param userID ID of teacher
     * @return Map<DayOfWeek, TimetableDay>
     * @throws IllegalArgumentException Thrown, if
     */
    suspend fun fetchTeacherTimetable(userID: Int): Pair<Map<DayOfWeek, Array<TeacherLesson>>?, Map<DayOfWeek, Array<TeacherLesson>>?> {
        val response = client.request<HttpResponse> {
            url.takeFrom("${subdomainURL}teacher/$userID/timetable")
            method = HttpMethod.Get
        }
        val pageString = response.receive<String>()
        val firstShiftMap = mutableMapOf<DayOfWeek, Array<TeacherLesson>>()
        for (i in 0..5) {
            firstShiftMap[DayOfWeek.values()[i]] = arrayOf()
        }
        val secondShiftMap = firstShiftMap.toMap().toMutableMap()

        try {
            htmlDocument(pageString) {
                table {
                    findAll {
                        forEachIndexed { _, docElement ->
                            var table = arrayOf<Array<TeacherLesson?>>()
                            var isFirstShift = true
                            docElement.tbody {
                                tr {
                                    findAll {
                                        forEachIndexed { lessonIndex, mainDoc ->
                                            val timeConstraints =
                                                mainDoc.td { withClass = "bells"; findFirst { ownText } }
                                                    .toTimeConstraint()
                                                    ?: TimeConstraints(0, 0, 0, 0)
                                            if (lessonIndex == 1 && timeConstraints.startHour !in 8..11)
                                                isFirstShift = false
                                            table += arrayOfNulls(6)
                                            mainDoc.td {
                                                findAll {
                                                    this.filter {
                                                        it.className != "bells" && it.className != "num"
                                                    }.forEachIndexed { dayIndex, docElement ->
                                                        if (docElement.ownText.isEmpty()) {
                                                            docElement.div {
                                                                withClass = "lesson"
                                                                findFirst {
                                                                    val name = try {
                                                                        b { findFirst { ownText } }
                                                                    } catch (e: ElementNotFoundException) {
                                                                        a {
                                                                            withClass = "subject"; findFirst { ownText }
                                                                        }
                                                                    }
                                                                    val classID =
                                                                        span { a { findFirst { eachHref.firstOrNull() } } }?.removePrefix(
                                                                            "/class/"
                                                                        )?.toInt() ?: 0

                                                                    table[lessonIndex][dayIndex] = TeacherLesson(
                                                                        place = lessonIndex.toShort(),
                                                                        title = name,
                                                                        schedule = timeConstraints,
                                                                        classID = classID
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (isFirstShift) {
                                table.forEachIndexed { _, arr ->
                                    arr.forEachIndexed { dayNum, it ->
                                        if (it != null) {
                                            firstShiftMap[DayOfWeek.values()[dayNum]] =
                                                firstShiftMap[DayOfWeek.values()[dayNum]]?.plus(it) ?: arrayOf()
                                        }
                                    }
                                }
                            } else {
                                table.forEachIndexed { _, arr ->
                                    arr.forEachIndexed { dayNum, it ->
                                        if (it != null) {
                                            secondShiftMap[DayOfWeek.values()[dayNum]] =
                                                secondShiftMap[DayOfWeek.values()[dayNum]]?.plus(it) ?: arrayOf()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: ElementNotFoundException) {
            return Pair(null, null)
        }
        return Pair<Map<DayOfWeek, Array<TeacherLesson>>?, Map<DayOfWeek, Array<TeacherLesson>>?>(
            first = if (firstShiftMap.filter { it.value.isNotEmpty() }.isNotEmpty()) firstShiftMap else null,
            second = if (secondShiftMap.filter { it.value.isNotEmpty() }.isNotEmpty()) secondShiftMap else null
        )
    }
}
