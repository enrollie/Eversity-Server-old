/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.AbsencePlacer
import by.enrollie.eversity.controllers.AuthController
import by.enrollie.eversity.data_classes.ErrorResponse
import by.enrollie.eversity.data_classes.UserID
import by.enrollie.eversity.data_classes.UserType
import by.enrollie.eversity.data_classes.evaluateToUserID
import by.enrollie.eversity.database.functions.doesUserExist
import by.enrollie.eversity.database.functions.getUserTokensCount
import by.enrollie.eversity.database.functions.invalidateAllTokens
import by.enrollie.eversity.database.functions.invalidateSingleToken
import by.enrollie.eversity.exceptions.AuthorizationUnsuccessful
import by.enrollie.eversity.security.User
import com.neitex.SchoolsByUnavailable
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
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
        invalidateAllTokens(user.id)
        call.respond(HttpStatusCode.OK, "")
    }
    delete("/current") {
        val user = call.principal<User>()!!
        invalidateSingleToken(user.id, user.token)
        call.respond(HttpStatusCode.OK, "")
    }
}

fun Route.authRoute() {
    route("/auth") {
        authLogin()
        route("/tokens") {
            tokens()
        }
        route("/{userId}/tokens") {
            get {
                val user = call.principal<User>()!!
                if (user.type != UserType.SYSTEM)
                    return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse.forbidden)
                val userID: UserID =
                    call.parameters["userId"]?.toIntOrNull()?.evaluateToUserID(user.id)
                        ?: throw ParameterConversionException("userId", "Int")
                call.respond(HttpStatusCode.OK, "{\"count\":${getUserTokensCount(userID)}}")
            }
            delete {
                val user = call.principal<User>()!!
                if (user.type != UserType.SYSTEM)
                    return@delete call.respond(HttpStatusCode.Forbidden, ErrorResponse.forbidden)
                val userID: UserID =
                    call.parameters["userId"]?.toIntOrNull()?.evaluateToUserID(user.id)
                        ?: throw ParameterConversionException("userId", "Int")
                if (!doesUserExist(userID))
                    return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse.userNotFound(userID))
                invalidateAllTokens(userID)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
