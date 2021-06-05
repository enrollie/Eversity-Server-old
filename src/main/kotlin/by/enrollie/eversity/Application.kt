/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity

import by.enrollie.eversity.controllers.AuthController
import by.enrollie.eversity.controllers.Registrar
import by.enrollie.eversity.database.initDatabase
import by.enrollie.eversity.database.validTokensList
import by.enrollie.eversity.plugins.configureAuthentication
import by.enrollie.eversity.plugins.configureBanner
import by.enrollie.eversity.plugins.configureHTTP
import io.ktor.application.*
import by.enrollie.eversity.routes.registerPupilsRouting
import by.enrollie.eversity.routes.registerAuthRoute
import by.enrollie.eversity.security.EversityJWT
import io.ktor.config.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.serialization.*
import kotlinx.coroutines.*
import org.joda.time.DateTime
import org.joda.time.Minutes
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

public const val EVERSITY_PUBLIC_NAME = "Eversity v0.0.1-alpha3"
public const val EVERSITY_WEBSITE = "https://github.com/enrollie/Eversity-Server"

/**
 * Starts given action and repeats it every (repeatMillis) milliseconds
 * @param repeatMillis Periodicity of running task in milliseconds
 * @param action Action, that needs to be repeated
 */
fun CoroutineScope.launchPeriodicAsync(repeatMillis: Long, action: () -> Unit) =
    this.async { //CoroutineScope extension to run task with periodicity of given repeatMillis
        while (isActive) {
            action()
            delay(repeatMillis)
        }
    }

public var configSubdomainURL: String? = null
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
    val tokenCacheValidityMinutes =
        environment.config.config("eversity").property("tokenCacheLifetime").getString().toInt()

    configSubdomainURL = environment.config.config("schools").propertyOrNull("subdomain")?.getString()
    configureAuthentication()
    configureHTTP()
    registerAuthRoute()
    registerPupilsRouting()
    CoroutineScope(this.coroutineContext).launchPeriodicAsync(TimeUnit.MINUTES.toMillis(tokenCacheValidityMinutes.toLong())) {
        var removedCount = 0
        validTokensList.removeIf {
            if (Minutes.minutesBetween(it.third, DateTime.now()).minutes >= tokenCacheValidityMinutes) {
                removedCount++
                true
            } else
                false

        }
        if (removedCount > 0)
            log.info("Removed $removedCount tokens from valid tokens cache!")
    }
}
