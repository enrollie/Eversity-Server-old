/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.N_Placer
import by.enrollie.eversity.controllers.AuthController
import by.enrollie.eversity.data_classes.ErrorResponse
import by.enrollie.eversity.database.functions.invalidateAllTokens
import by.enrollie.eversity.database.functions.invalidateSingleToken
import by.enrollie.eversity.exceptions.AuthorizationUnsuccessful
import by.enrollie.eversity.security.User
import com.neitex.SchoolsByUnavailable
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
import org.joda.time.DateTime
import org.joda.time.Seconds

fun Route.authRoutes() {
    val authController = AuthController()
    route("/api/auth") {
        authenticate("jwt") {
            route("/invalidate") {
                post("/all") {
                    val user = call.authentication.principal<User>() ?: return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        "Authentication failed. Check your token."
                    )
                    val removedTokenCount = invalidateAllTokens(user.id)
                    return@post call.respond(
                        HttpStatusCode.OK,
                        Json.encodeToString(mapOf("removed_tokens" to removedTokenCount))
                    )
                }
                post("/current") {
                    val user = call.authentication.principal<User>() ?: return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        "Authentication failed. Check your token."
                    )
                    invalidateSingleToken(user.id, user.token)
                    return@post call.respond(
                        HttpStatusCode.OK
                    )
                }
            }
            get("/check") {
                call.authentication.principal<User>() ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    "Authentication failed. Check your token."
                )
                call.respond(HttpStatusCode.OK)
            }
        }
        post("/login") {
            if (!N_Placer.schoolsByAvailability) {
                call.response.headers.append(
                    HttpHeaders.RetryAfter,
                    Seconds.secondsBetween(DateTime.now(), N_Placer.nextSchoolsByCheck).seconds.toString()
                )
                return@post call.respond(HttpStatusCode.ServiceUnavailable)
            }
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
            val authResult = authController.loginUser(username, password)
            when (authResult.isSuccess) {
                true -> return@post call.respond(HttpStatusCode.OK, authResult.getOrNull().toString())
                false -> {
                    when (authResult.exceptionOrNull()) {
                        is AuthorizationUnsuccessful -> {
                            return@post call.respond(HttpStatusCode.Unauthorized)
                        }
                        is SchoolsByUnavailable -> {
                            call.response.headers.append(
                                HttpHeaders.RetryAfter,
                                Seconds.secondsBetween(DateTime.now(), N_Placer.nextSchoolsByCheck).seconds.toString()
                            )
                            return@post call.respond(HttpStatusCode.ServiceUnavailable)
                        }
                        else -> {
                            return@post call.respond(
                                HttpStatusCode.InternalServerError,
                                Json.encodeToString(
                                    ErrorResponse(
                                        authResult.exceptionOrNull()?.javaClass?.name.toString(),
                                        authResult.exceptionOrNull()?.message.toString()
                                    )
                                )
                            )
                        }
                    }
                }
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
