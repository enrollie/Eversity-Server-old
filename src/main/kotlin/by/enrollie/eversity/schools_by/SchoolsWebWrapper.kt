package by.enrollie.eversity.schools_by

import by.enrollie.eversity.data_classes.Pupil
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import it.skrape.core.htmlDocument
import it.skrape.exceptions.ElementNotFoundException
import it.skrape.selects.html5.a
import it.skrape.selects.html5.div
import it.skrape.selects.html5.form


/**
 * A wrapper for parsing and making data out of Schools.by HTML pages
 * @author Pavel Matusevich
 */
class SchoolsWebWrapper {
    private val userAgent = "Educad/1.0 (Windows NT 10.0; Win32; x86; rv:1.0) KTor/1.5.4 Educad/1.0"

    private var subdomainURL: String = "https://demo.schools.by/"
    private var schoolsBYCookies: Pair<String, String> = Pair("", "")

    private var client = HttpClient {
        followRedirects = true
        expectSuccess = false
        defaultRequest {
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
        subdomainURL = "https://demo.schools.by/"
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
        val baseCSRFresponse = client.get<HttpResponse>("https://schools.by/login") {
            headers.append(HttpHeaders.UserAgent, userAgent)
            userAgent(userAgent)
        }
        var csrfTokenCookie = baseCSRFresponse.setCookie().find { it.name == "csrftoken" }
            ?: throw NoSuchElementException("CSRFToken not found after baseCSRFresponse")
        var csrfToken = csrfTokenCookie.value
        val finalCSRFresponse: HttpResponse = client.config {
            followRedirects = false
        }.submitForm(
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
        println(finalCSRFresponse.call.request.content.contentType)

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
    suspend fun checkCookies(cookies:Pair<String,String>? = null, changeInternal:Boolean = true):Boolean{
        val response = HttpClient().use {
            return@use it.request<HttpResponse>{
                url.takeFrom("${subdomainURL}teachers")
                method = HttpMethod.Get
                cookie("csrftoken", cookies?.first ?: schoolsBYCookies.first)
                cookie("sessionid", cookies?.second ?: schoolsBYCookies.second)
            }
        }
        try {
            val html = response.receive<String>()
            htmlDocument(html){
                form{
                    withAttribute = Pair("method", "post")
                    findAll{
                        //If found, cookies are not valid. Will return false at end.
                    }
                }
            }
        } catch (e: NoTransformationFoundException){
            //TODO: Add log
                e.printStackTrace()
            return false //Did not recieve page correctly.
        } catch (e: ElementNotFoundException){
            if(changeInternal && cookies != null){
                schoolsBYCookies = cookies
            }
            return true //Login form not found => we are logged in.
        }
        return false //login form found (no exception were thrown)
    }

    /**
     *Only for use with teacher's account. Returns unsorted array of [Pupil].
     *
     * @param classID ID of needed class
     *
     * @return [Array] of [Pupil] from given [classID]
     * @throws IllegalArgumentException Thrown, if no pupils found.
     */
    suspend fun getPupilsArray(classID: Int): Array<Pupil> {
        val response = client.request<HttpResponse>{
            url.takeFrom("$subdomainURL/class/$classID")
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
            throw IllegalArgumentException()
        }
        return pupilsArray
    }
}