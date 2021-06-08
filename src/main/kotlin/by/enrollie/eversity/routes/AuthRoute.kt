/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.controllers.AuthController
import by.enrollie.eversity.database.EversityDatabase
import by.enrollie.eversity.exceptions.AuthorizationUnsuccessful
import by.enrollie.eversity.exceptions.UserNotRegistered
import by.enrollie.eversity.security.User
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun Route.authRoutes() {
    val authController = AuthController()
    route("/api/auth") {
        authenticate("jwt") {
            route("/invalidate") {
                post("/all") {
                    val user = call.authentication.principal<User>() ?: return@post call.respond(
                        HttpStatusCode.Forbidden,
                        "Authentication failed. Check your token."
                    )
                    val removedTokenCount = EversityDatabase.invalidateAllTokens(user.id, "USER_REQUEST")
                    return@post call.respond(
                        HttpStatusCode.OK,
                        Json.encodeToString(mapOf("removed_tokens" to removedTokenCount))
                    )
                }
                post("/current") {
                    val user = call.authentication.principal<User>() ?: return@post call.respond(
                        HttpStatusCode.Forbidden,
                        "Authentication failed. Check your token."
                    )
                   EversityDatabase.invalidateSingleToken(user.id, user.token, "USER_REQUEST")
                    return@post call.respond(
                        HttpStatusCode.OK
                    )
                }
            }
        }
        post("/login") {
            val loginJSON: JsonObject
            try {
                val req = call.receive<String>()
                loginJSON = Json.parseToJsonElement(req).jsonObject
            } catch (e: ContentTransformationException) {
                application.log.debug("Deserialization failed. ${e.message}", e)
                return@post call.respond(HttpStatusCode.BadRequest, "Deserialization failed: ${e.localizedMessage}")
            } catch (e: SerializationException) {
                application.log.debug("Deserialization failed", e)
                return@post call.respond(HttpStatusCode.BadRequest, "Malformed JSON")
            } catch (e: IllegalArgumentException) {
                application.log.debug("Deserialization failed", e)
                return@post call.respond(HttpStatusCode.BadRequest, "Malformed JSON")
            }
            if (!loginJSON.containsKey("username") || !loginJSON.containsKey("password")) {
                return@post call.respondText(
                    "JSON does not contain username or password.",
                    contentType = ContentType.Text.Plain,
                    status = HttpStatusCode.BadRequest
                )
            }
            val username = loginJSON["username"]!!.jsonPrimitive.content
            val password = loginJSON["password"]!!.jsonPrimitive.content
            try {
                call.respond(HttpStatusCode.OK, authController.loginUser(username, password))
                return@post
            } catch (e: UserNotRegistered) {
                //Proceed to registration
            } catch (e: AuthorizationUnsuccessful) {
                return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    "Schools.by did not accept your login data. Try checking it twice."
                )
            }
            try {
                call.respond(HttpStatusCode.OK, authController.registerUser(username, password))
                return@post
            } catch (e: AuthorizationUnsuccessful) {
                return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    "Schools.by did not accept your login data. Try checking it twice."
                )
            }
        }
        get("/login") {
            return@get call.respond(HttpStatusCode.BadRequest, "Method GET is not allowed.")
        }
    }
}

fun Application.registerAuthRoute() {
    routing {
        authRoutes()
    }
}