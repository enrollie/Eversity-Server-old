/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

interface User {
    val id: Int
    val type: UserType
    val firstName: String
    val middleName: String?
    val lastName: String
}

typealias UserID = Int
