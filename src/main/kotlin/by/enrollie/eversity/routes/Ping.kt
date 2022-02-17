package by.enrollie.eversity.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.pingRoute() {
    route("/ping") {
        get {
            call.respond(mapOf("message" to "Pong!"))
        }
    }
}
