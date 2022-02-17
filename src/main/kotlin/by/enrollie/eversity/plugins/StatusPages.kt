/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.plugins

import by.enrollie.eversity.data_classes.ErrorResponse
import by.enrollie.eversity.exceptions.NoSuchSchoolClassException
import by.enrollie.eversity.exceptions.NoSuchUserException
import com.osohq.oso.Exceptions
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*

fun Application.installExceptionStatus() {
    install(StatusPages) {
        exception<NoSuchSchoolClassException> {
            call.respond(HttpStatusCode.NotFound, ErrorResponse.genericNotFound)
        }
        exception<NoSuchUserException> {
            call.respond(HttpStatusCode.NotFound, ErrorResponse.genericNotFound)
        }
        exception<Exceptions.ForbiddenException> {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse.forbidden)
        }
        exception<Exceptions.NotFoundException> {
            call.respond(HttpStatusCode.NotFound, ErrorResponse.genericNotFound)
        }
        exception<ContentTransformationException> {
            call.respond(HttpStatusCode.NotFound, ErrorResponse.genericDeserializationException)
        }
        exception<MissingRequestParameterException> {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse.missingQueryParameter)
        }
    }
}
