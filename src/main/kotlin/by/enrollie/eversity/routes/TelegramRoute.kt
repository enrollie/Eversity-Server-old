/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.data_classes.APIUserType
import by.enrollie.eversity.database.functions.doesUserExist
import by.enrollie.eversity.database.functions.getUserType
import by.enrollie.eversity.database.functions.removeTelegramNotifyData
import by.enrollie.eversity.schools_by.SchoolsAPIClient
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.joda.time.DateTime
import org.joda.time.Minutes
import java.util.concurrent.TimeUnit
import kotlin.random.Random

val telegramPairingCodesList = mutableListOf<Pair<Short, Triple<Int, List<Int>, DateTime>>>()

private val random = Random("Eversity".hashCode())
fun Route.telegramRoute() {
    authenticate("jwt") {
        route("/api/telegram") {
            get("/pair") {
                repeat(random.nextInt(2, 15)) {
                    random.nextInt()
                }
                val userJWT =
                    call.authentication.principal<by.enrollie.eversity.security.User>() ?: return@get call.respond(
                        HttpStatusCode.Unauthorized,
                        "Authentication failed. Check your token."
                    )
                if (telegramPairingCodesList.find { it.second.first == userJWT.id } != null) {
                    val existingCode = telegramPairingCodesList.find { it.second.first == userJWT.id } ?: Pair(
                        0,
                        Triple(0, listOf(), DateTime.parse("1991-01-02"))
                    )
                    if (Minutes.minutesBetween(existingCode.second.third, DateTime.now()).minutes < 14)
                        return@get call.respond(
                            status = HttpStatusCode.OK,
                            message = Json.encodeToString(
                                mapOf(
                                    "pairID" to existingCode.first.toString(),
                                    "minutesUntilExpire" to "${
                                        Minutes.minutesBetween(
                                            existingCode.second.third.minusMinutes(15),
                                            DateTime.now()
                                        ).minutes
                                    }"
                                )
                            )
                        )
                    else {
                        telegramPairingCodesList.removeIf {
                            Minutes.minutesBetween(it.second.third, DateTime.now()).minutes >= 14
                        }
                    }
                }
                if (getUserType(userJWT.id) != APIUserType.Parent) {
                    return@get call.respondText(
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.BadRequest,
                        text = Json.encodeToString(
                            mapOf(
                                "errorCode" to "USER_NOT_PARENT",
                                "action" to "NOTHING"
                            )
                        )
                    )
                }
                var newCode = random.nextInt(100, 9999).toShort()
                if (telegramPairingCodesList.find { it.first == newCode } != null)
                    newCode = random.nextInt(100, 9999).toShort()
                val schoolsAPIClient =
                    SchoolsAPIClient() //as Schools.by did not bother to secure data in their system, getting parent's pupils does not require any authorization
                val pupilsList = try {
                    schoolsAPIClient.fetchParentsPupils(parentID = userJWT.id)
                } catch (e: UnknownError) {
                    return@get call.respondText(
                        status = HttpStatusCode.FailedDependency,
                        text = "Exception message: ${e.message}"
                    )
                }
                for (pupil in pupilsList) {
                    if (!doesUserExist(pupil.id))
                        return@get call.respondText(
                            text = "Pupil with ID ${pupil.id} (First name: ${pupil.firstName}; Last name: ${pupil.lastName}) is not yet registered by teacher.",
                            status = HttpStatusCode.PreconditionFailed
                        )
                }
                telegramPairingCodesList.add(
                    Pair(
                        newCode,
                        Triple(userJWT.id, pupilsList.map { it.id }, DateTime.now())
                    )
                )
                CoroutineScope(Dispatchers.Default).launch {
                    delay(TimeUnit.MINUTES.toMillis(14))
                    telegramPairingCodesList.removeIf {
                        it.first == newCode
                    }
                }
                return@get call.respond(
                    HttpStatusCode.OK, Json.encodeToString(
                        mapOf(
                            "pairID" to newCode.toString(),
                            "minutesUntilExpire" to "14"
                        )
                    )
                )
            }
            post("/unpair") {
                val userJWT =
                    call.authentication.principal<by.enrollie.eversity.security.User>() ?: return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        "Authentication failed. Check your token."
                    )
                removeTelegramNotifyData(userJWT.id)
                return@post call.respond(HttpStatusCode.OK, "")
            }
        }
    }
}

fun Application.registerTelegramRoute() {
    routing {
        telegramRoute()
    }
}