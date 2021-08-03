/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

@file:Suppress("DeferredResultUnused")

package by.enrollie.eversity

import by.enrollie.eversity.controllers.AuthController
import by.enrollie.eversity.controllers.Registrar
import by.enrollie.eversity.data_classes.SchoolNameDeclensions
import by.enrollie.eversity.database.initDatabase
import by.enrollie.eversity.database.validTokensSet
import by.enrollie.eversity.notifier.EversityNotifier
import by.enrollie.eversity.placer.EversityPlacer
import by.enrollie.eversity.plugins.configureAuthentication
import by.enrollie.eversity.plugins.configureBanner
import by.enrollie.eversity.plugins.configureHTTP
import by.enrollie.eversity.routes.*
import by.enrollie.eversity.schools_by.CredentialsChecker
import by.enrollie.eversity.security.EversityJWT
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.config.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.joda.time.DateTime
import org.joda.time.Minutes
import java.io.File
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.TimeUnit

var EVERSITY_PUBLIC_NAME = "Eversity "
private var EVERSITY_VERSION = ""
var EVERSITY_BUILD_DATE = ""
const val EVERSITY_WEBSITE = "https://github.com/enrollie/Eversity-Server"
lateinit var EversityBot: EversityNotifier
lateinit var N_Placer: EversityPlacer //name is taken from my previous project
lateinit var SchoolsCredentialsChecker: CredentialsChecker
lateinit var SCHOOL_NAME: SchoolNameDeclensions
lateinit var SCHOOL_WEBSITE: String

/**
 * Starts given action and repeats it every (repeatMillis) milliseconds
 * @param repeatMillis Periodicity of running task in milliseconds
 * @param action Action, that needs to be repeated
 */
fun CoroutineScope.launchPeriodicAsync(repeatMillis: Long, action: suspend () -> Unit) =
    this.async { //CoroutineScope extension to run task with periodicity of given repeatMillis
        while (isActive) {
            action()
            delay(repeatMillis)
        }
    }

var configSubdomainURL: String? = null
var tokenCacheValidityMinutes: Int = 60

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(ContentNegotiation) {
        json()
        gson {
        }
    }
    kotlin.run {
        val props = Properties()
        props.load(this.javaClass.getResourceAsStream("/appInfo.properties"))
        EVERSITY_VERSION = props.getProperty("appVersion")
        EVERSITY_PUBLIC_NAME += EVERSITY_VERSION
        EVERSITY_BUILD_DATE = props.getProperty("buildDate")
    }
    kotlin.run {
        val namingFileName = System.getProperty("SCHOOL_NAMING_FILE", "school_naming.properties")
        val namingFile = File(namingFileName)
        val props = Properties()
        props.load(namingFile.reader(charset("UTF-8")))
        try {
            SCHOOL_NAME = SchoolNameDeclensions(
                props.getProperty("nominative"),
                props.getProperty("genitive"),
                props.getProperty("accusative"),
                props.getProperty("dative"),
                props.getProperty("instrumental"),
                props.getProperty("prepositional"),
                props.getProperty("location")
            )
        } catch (e: NoSuchElementException) {
            throw ApplicationConfigurationException("School naming file error: ${e.message}")
        }
    }
    configureBanner()
    //JWT generator initialization
    val secretJWT = this.environment.config.config("jwt").property("secret").getString()
    EversityJWT.initialize(secretJWT)

    AuthController.initialize(this.log)
    Registrar.initialize(this.log)

    //Database initialization
    val host = this.environment.config.config("database").property("host").getString()
    val port = this.environment.config.config("database").property("port").getString()
    val databaseName = this.environment.config.config("database").property("name").getString()
    val user = this.environment.config.config("database").property("user").getString()
    val password = this.environment.config.config("database").property("password").getString()
    initDatabase(host, port, databaseName, user, password)
    tokenCacheValidityMinutes =
        environment.config.config("eversity").property("tokenCacheLifetime").getString().toInt()

    configSubdomainURL = environment.config.config("schools").property("subdomain").getString()

    val telegramToken = environment.config.config("telegram").property("botToken").getString()

    SCHOOL_WEBSITE = environment.config.config("school").property("website").getString()
    EversityBot = EversityNotifier(telegramToken)
    N_Placer = EversityPlacer(org.slf4j.LoggerFactory.getLogger("Eversity"))
    SchoolsCredentialsChecker =
        CredentialsChecker(
            environment.config.config("eversity").property("autoCredentialsRecheck").getString().toInt(),
            org.slf4j.LoggerFactory.getLogger("Schools.by")
        )

    configureAuthentication()
    configureHTTP()
    configurePingRouting()
    registerClassesRoute()
    registerTelegramRoute()
    registerAuthRoute()
    registerUsersRoute()
    registerDiary()
    registerAbsenceRoute()
    registerTeachers()
    //TODO: Remove, when web client is done
    routing {
        get("/") {
            return@get call.respondText(
                status = HttpStatusCode.Locked,
                text = "Web client is not ready yet. Check for updates on $EVERSITY_WEBSITE!"
            )
        }
    }

    CoroutineScope(this.coroutineContext).launchPeriodicAsync(TimeUnit.MINUTES.toMillis(tokenCacheValidityMinutes.toLong())) {
        //Periodically clears valid tokens cache (to ensure some kind of security)
        //Periodicity is set by tokenCacheValidityMinutes variable
        var removedCount = 0
        validTokensSet.removeIf {
            if (Minutes.minutesBetween(it.third, DateTime.now()).minutes >= tokenCacheValidityMinutes) {
                removedCount++
                true
            } else
                false
        }
        if (removedCount > 0)
            log.info("Removed $removedCount tokens from valid tokens cache!")
    }

    CoroutineScope(this.coroutineContext).launchPeriodicAsync(TimeUnit.MINUTES.toMillis(5_0L)) {
        val isSchoolsByAvailable = checkSchoolsByAvailability()
        if (isSchoolsByAvailable != N_Placer.getSchoolsByAvailability())
            N_Placer.setSchoolsByAvailability(isSchoolsByAvailable)
    }

}

private suspend fun checkSchoolsByAvailability(): Boolean {
    return try {
        val response = HttpClient {
            this.expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = TimeUnit.SECONDS.toMillis(30.toLong())
                connectTimeoutMillis = TimeUnit.SECONDS.toMillis(30.toLong())
                socketTimeoutMillis = TimeUnit.SECONDS.toMillis(30.toLong())
            }
        }.use {
            it.get<HttpResponse> {
                url.takeFrom(configSubdomainURL!!)
            }
        }
        return response.status == HttpStatusCode.OK
    } catch (e: HttpRequestTimeoutException) {
        false
    } catch (e: UnknownHostException) {
        false
    }
}
