package by.enrollie.eversity

import by.enrollie.eversity.database.initDatabase
import by.enrollie.eversity.plugins.configureHTTP
import io.ktor.application.*
import by.enrollie.eversity.routes.registerPupilsRouting
import by.enrollie.eversity.routes.registerRegistrationRoutes
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.serialization.*

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)

/**
 * Please note that you can use any other name instead of *module*.
 * Also note that you can have more then one modules in your application.
 * */
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(ContentNegotiation) {
        json()
        gson {
        }
    }
    configureHTTP()
    registerRegistrationRoutes()
    registerPupilsRouting()

    val host = this.environment.config.config("database").property("databaseHost").getString()
    val port = this.environment.config.config("database").property("databasePort").getString()
    val databaseName = this.environment.config.config("database").property("databaseName").getString()
    val user = this.environment.config.config("database").property("databaseUser").getString()
    val password = this.environment.config.config("database").property("databasePassword").getString()
    initDatabase(host, port, databaseName, user, password)
}
