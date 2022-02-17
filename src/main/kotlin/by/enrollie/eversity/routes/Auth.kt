/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.AbsencePlacer
import by.enrollie.eversity.OSO
import by.enrollie.eversity.controllers.AuthController
import by.enrollie.eversity.data_classes.ErrorResponse
import by.enrollie.eversity.data_classes.UserID
import by.enrollie.eversity.data_classes.evaluateToUserID
import by.enrollie.eversity.database.functions.*
import by.enrollie.eversity.exceptions.AuthorizationUnsuccessful
import by.enrollie.eversity.security.User
import by.enrollie.eversity.uac.OsoUser
import com.neitex.SchoolsByUnavailable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
private data class SchoolsByAuthData(val username: String, val password: String)

private fun Route.authLogin() {
    post("/login") {
        if (!AbsencePlacer.schoolsByAvailability) {
            val nextRecheck = AbsencePlacer.nextSchoolsByCheckIn
            call.response.headers.append(
                HttpHeaders.RetryAfter,
                nextRecheck.seconds.toString()
            )
            return@post call.respondText(
                text = "Schools.by in unavailable right now. Next check is in ${nextRecheck.seconds} seconds",
                status = HttpStatusCode.ServiceUnavailable
            )
        }
        val loginData = call.receive<SchoolsByAuthData>()
        val loginResult = AuthController.loginUser(loginData.username, loginData.password)
        return@post when (loginResult.isSuccess) {
            true -> {
                val unfolded = loginResult.getOrThrow()
                call.respond(
                    HttpStatusCode.OK,
                    mapOf("userID" to unfolded.second.toString(), "token" to unfolded.first)
                )
            }
            false -> {
                when (loginResult.exceptionOrNull()) {
                    is AuthorizationUnsuccessful -> {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponse.schoolsByAuthFail)
                    }
                    is SchoolsByUnavailable -> {
                        val nextRecheck = AbsencePlacer.nextSchoolsByCheckIn
                        call.response.headers.append(
                            HttpHeaders.RetryAfter,
                            nextRecheck.seconds.toString()
                        )
                        call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse.schoolsByUnavailable)
                    }
                    else -> {
                        LoggerFactory.getLogger("AuthRoute-Login")
                            .info(
                                "Login routine for user with username ${loginData.username} returned unexpected exception",
                                loginResult.exceptionOrNull()!!
                            )
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse.exception(loginResult.exceptionOrNull()!!)
                        )
                    }
                }
            }
        }
    }
}

private fun Route.tokens() {
    delete {
        val user = call.principal<User>()!!
        OSO.authorize(OsoUser(user.user.id, user.user.type), "invalidate_tokens", OsoUser(user.user.id, user.user.type))
        invalidateAllTokens(user.user.id)
        call.respond(HttpStatusCode.OK, "")
    }
    delete("/current") {
        val user = call.principal<User>()!!
        OSO.authorize(OsoUser(user.user.id, user.user.type), "invalidate_tokens", OsoUser(user.user.id, user.user.type))
        invalidateSingleToken(user.user.id, user.accessToken)
        call.respond(HttpStatusCode.OK, "")
    }
}

fun Route.authRoute() {
    route("/auth") {
        authLogin()
        authenticate("jwt") {
            route("/tokens") {
                tokens()
            }
            route("/{userId}/tokens") {
                get {
                    val user = call.principal<User>()!!
                    val userID: UserID =
                        call.parameters["userId"]?.toIntOrNull()?.evaluateToUserID(user.user.id)
                            ?: throw ParameterConversionException("userId", "Int")
                    OSO.authorize(OsoUser(user.user.id, user.user.type),
                        "read_tokens",
                        OsoUser(userID, getUserType(userID)))
                    call.respond(HttpStatusCode.OK, "{\"count\":${getUserTokensCount(userID)}}")
                }
                delete {
                    val user = call.principal<User>()!!
                    val userID: UserID =
                        call.parameters["userId"]?.toIntOrNull()?.evaluateToUserID(user.user.id)
                            ?: throw ParameterConversionException("userId", "Int")
                    if (!doesUserExist(userID))
                        return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse.userNotFound(userID))
                    OSO.authorize(OsoUser(user.user.id, user.user.type),
                        "invalidate_tokens",
                        OsoUser(userID, getUserType(userID)))
                    invalidateAllTokens(userID)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}
