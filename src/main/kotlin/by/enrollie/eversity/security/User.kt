/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.security

import by.enrollie.eversity.data_classes.UserType
import io.ktor.auth.*

class User(ID: Int, userType: UserType, accessToken: String) :Principal {
    val id: Int = ID
    val type: UserType = userType
    val token:String = accessToken
}
