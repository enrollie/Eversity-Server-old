/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

@file:Suppress("DeferredResultUnused")

package by.enrollie.eversity

import by.enrollie.eversity.controllers.LocalLoginIssuer
import by.enrollie.eversity.data_classes.SchoolNameDeclensions
import by.enrollie.eversity.database.initXodusDatabase
import by.enrollie.eversity.notifier.EversityNotifier
import by.enrollie.eversity.placer.EversityPlacer
import by.enrollie.eversity.plugins.configureAuthentication
import by.enrollie.eversity.plugins.configureBanner
import by.enrollie.eversity.plugins.configureHTTP
import by.enrollie.eversity.routes.authRoute
import by.enrollie.eversity.schools_by.CredentialsChecker
import by.enrollie.eversity.security.EversityJWT
import com.neitex.SchoolsByParser
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.websocket.*
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Duration
import java.util.*

var EVERSITY_PUBLIC_NAME = "Eversity "
    private set
private var EVERSITY_VERSION = ""
var EVERSITY_BUILD_DATE = ""
    private set
const val EVERSITY_WEBSITE = "https://github.com/enrollie/Eversity-Server"
lateinit var EversityBot: EversityNotifier
    private set
lateinit var AbsencePlacer: EversityPlacer
    private set
lateinit var SchoolsCredentialsChecker: CredentialsChecker
    private set
lateinit var SCHOOL_NAME: SchoolNameDeclensions
    private set
lateinit var SCHOOL_WEBSITE: String
    private set
lateinit var LOCAL_ACCOUNT_ISSUER: LocalLoginIssuer
    private set
lateinit var DATABASE: TransientEntityStore
    private set

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

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)

fun Application.registerRoutes() {
    routing {
        authRoute()
    }
}

@Suppress("unused") // Referenced in application.conf
fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(1)
        timeout = Duration.ofSeconds(15)
        masking = false
    }
    kotlin.run {
        val props = Properties()
        props.load(this.javaClass.getResourceAsStream("/appInfo.properties"))
        EVERSITY_VERSION = props.getProperty("appVersion")
        EVERSITY_PUBLIC_NAME += EVERSITY_VERSION
        EVERSITY_BUILD_DATE = props.getProperty("buildDate")
    }
    log.debug("Eversity Core ${EVERSITY_VERSION}; Build date: $EVERSITY_BUILD_DATE")
    kotlin.run {
        val namingFileName = System.getenv("SCHOOL_NAMING_FILE")
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

    DATABASE =
        initXodusDatabase(File(environment.config.config("database").property("path").getString(), "eversity-db"))

    configSubdomainURL = environment.config.config("schools").property("subdomain").getString()

    val telegramToken = environment.config.config("telegram").property("botToken").getString()

    SCHOOL_WEBSITE = environment.config.config("school").property("website").getString()
    EversityBot = EversityNotifier(telegramToken)
    AbsencePlacer = EversityPlacer(org.slf4j.LoggerFactory.getLogger("Eversity"))
    SchoolsCredentialsChecker =
        CredentialsChecker(
            environment.config.config("eversity").property("autoCredentialsRecheck").getString().toInt(),
            org.slf4j.LoggerFactory.getLogger("Schools.by")
        )
    kotlin.run {
        val localFile = File(environment.config.config("eversity").property("localAccountsFilePath").getString())
        LOCAL_ACCOUNT_ISSUER =
            if (!localFile.exists()) {
                log.warn("Local accounts file is not available at path \'${localFile.absolutePath}\'! No local accounts will be available")
                LocalLoginIssuer(mapOf())
            } else LocalLoginIssuer(Json.decodeFromString(localFile.readText()))
    }
    SchoolsByParser.setSubdomain(configSubdomainURL!!)

    configureAuthentication()
    configureHTTP()
    registerRoutes()
}
