/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

data class UserName(
    val firstName: String,
    val middleName: String?,
    val lastName: String
) {

    companion object {
        fun fromParserName(input: com.neitex.Name): UserName =
            UserName(input.firstName, input.middleName, input.lastName)
    }

    val fullForm: String
        get() = "$lastName $firstName".let {
            if (middleName != null)
                "$it $middleName"
            else it
        }
    val shortForm: String
        get() = if (middleName != null)
            "$firstName $middleName"
        else "$lastName $firstName"
}
