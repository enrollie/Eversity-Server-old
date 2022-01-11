/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.routes

import by.enrollie.eversity.data_classes.UserType
import by.enrollie.eversity.database.functions.getParentsPupils
import by.enrollie.eversity.database.functions.getUserType
import by.enrollie.eversity.database.functions.removeTelegramNotifyData
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.joda.time.DateTime
import org.joda.time.Minutes
import java.util.concurrent.TimeUnit
import kotlin.random.Random

val telegramPairingCodesList = mutableListOf<Pair<Short, Pair<Int, DateTime>>>()

private val random = Random("Eversity".hashCode())

@OptIn(ExperimentalSerializationApi::class)
fun Route.telegramRoute() {
    authenticate("jwt") {
        route("/api/telegram") {
            get("/pair") {
                val userJWT =
                    call.authentication.principal<by.enrollie.eversity.security.User>() ?: return@get call.respond(
                        HttpStatusCode.Unauthorized,
                        "Authentication failed. Check your token."
                    )
                if (telegramPairingCodesList.find { it.second.first == userJWT.id } != null) {
                    val existingCode = telegramPairingCodesList.find { it.second.first == userJWT.id } ?: Pair(
                        -1,
                        Pair(0, DateTime.parse("1991-01-02"))
                    )
                    if (Minutes.minutesBetween(existingCode.second.second, DateTime.now()).minutes < 14)
                        return@get call.respond(
                            status = HttpStatusCode.OK,
                            message = Json.encodeToString(
                                mapOf(
                                    "pairID" to existingCode.first.toString(),
                                    "minutesUntilExpire" to "${
                                        Minutes.minutesBetween(
                                            existingCode.second.second.minusMinutes(15),
                                            DateTime.now()
                                        ).minutes
                                    }"
                                )
                            )
                        )
                    else {
                        telegramPairingCodesList.removeIf {
                            Minutes.minutesBetween(it.second.second, DateTime.now()).minutes >= 14
                        }
                    }
                }
                if (getUserType(userJWT.id) != UserType.Parent) {
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
                val pupils = getParentsPupils(userJWT.id)
                telegramPairingCodesList.add(
                    Pair(
                        newCode,
                        Pair(userJWT.id, DateTime.now())
                    )
                )
                CoroutineScope(Dispatchers.Default).launch {
                    delay(TimeUnit.MINUTES.toMillis(14))
                    telegramPairingCodesList.removeIf {
                        it.first == newCode
                    }
                }
                call.respond(
                    HttpStatusCode.OK, Json.encodeToString(
                        mapOf(
                            "pairID" to newCode.toString(),
                            "minutesUntilExpire" to "14"
                        )
                    )
                )
                repeat(random.nextInt(2, this.context.attributes.hashCode())) {
                    random.nextInt()
                }
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
