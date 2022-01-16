/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import by.enrollie.eversity.AbsencePlacer
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val errorCode: String, val additionalInfo: String?) {
    companion object {
        fun userNotFound(userID: UserID) = ErrorResponse("USER_NOT_FOUND", "User with ID $userID not found")
        fun classNotFound(classID: ClassID) = ErrorResponse("CLASS_NOT_FOUND", "Class with ID $classID not found")
        fun integrationNotFound(integrationID: String) =
            ErrorResponse("INTEGRATION_NOT_FOUND", "Integration with ID \'$integrationID\' not found")

        fun exception(exception: Throwable) = ErrorResponse(
            "INTERNAL_SERVER_ERROR",
            "Server encountered an unknown exception: ${exception::class.java.simpleName}"
        )

        fun conflict(message: String) = ErrorResponse("CONFLICT", message)

        val forbidden: ErrorResponse
            get() = ErrorResponse("FORBIDDEN", "Access to this route is forbidden to this user")
        val schoolsByAuthFail: ErrorResponse
            get() = ErrorResponse("AUTHORIZATION_UNSUCCESSFUL", "Server rejected this login credentials")
        val schoolsByUnavailable: ErrorResponse
            get() = ErrorResponse(
                "SCHOOLS_BY_UNAVAILABLE",
                "Schools.by is unavailable. Next check is in ${AbsencePlacer.nextSchoolsByCheckIn.seconds} seconds"
            )
    }
}
