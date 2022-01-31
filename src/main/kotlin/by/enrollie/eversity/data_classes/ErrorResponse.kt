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
data class ErrorResponse private constructor(val errorCode: String, val additionalInfo: String?) {
    companion object {
        fun userNotFound(userID: UserID) = ErrorResponse("USER_NOT_FOUND", "User with ID $userID not found")
        fun classNotFound(classID: ClassID) = ErrorResponse("CLASS_NOT_FOUND", "Class with ID $classID not found")
        fun integrationNotFound(integrationID: String) =
            ErrorResponse("INTEGRATION_NOT_FOUND", "Integration with ID \'$integrationID\' not found")

        fun schoolClassViolation(classID: ClassID, userID: UserID) =
            ErrorResponse("SCHOOL_CLASS_VIOLATION", "User with ID $userID does not belong to class ID $classID")

        fun exception(exception: Throwable) = ErrorResponse(
            "INTERNAL_SERVER_ERROR",
            "Server encountered an unknown exception: ${exception::class.java.simpleName}"
        )

        fun illegalDate(date: String) = ErrorResponse("ILLEGAL_DATE", "Date $date is illegal")

        fun conditionalMissingRequiredQuery(conditionParameter: String, parameter: String) =
            ErrorResponse(
                "MISSING_CONDITIONAL_QUERY_PARAMETER",
                "When using parameter \'$conditionParameter\', parameter \'$parameter\' is required"
            )

        fun deserializationFailure(parameter: String, value: String) =
            ErrorResponse(
                "DESERIALIZATION_FAILURE",
                "Failed to deserialize parameter \'$parameter\' of value \'$value\'"
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
        val genericNotFound: ErrorResponse
            get() = ErrorResponse("NOT_FOUND", "Requested resource was not found")
        val genericDeserializationException: ErrorResponse
            get() = ErrorResponse("DESERIALIZATION_FAILURE", "Failed to deserialize input data")
    }
}
