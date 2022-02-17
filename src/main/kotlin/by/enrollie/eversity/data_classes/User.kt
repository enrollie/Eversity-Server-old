/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import kotlinx.serialization.SerialName

interface User {
    val id: Int

    @SerialName("userType")
    val type: UserType
    val firstName: String
    val middleName: String?
    val lastName: String
}

typealias UserID = Int

fun Int.evaluateToUserID(currentUserID: UserID): UserID = if (this == -1) currentUserID else this
