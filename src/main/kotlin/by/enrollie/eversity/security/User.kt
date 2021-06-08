/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.security

import by.enrollie.eversity.data_classes.APIUserType
import io.ktor.auth.*

class User(ID: Int, userType: APIUserType, accessToken: String) :Principal {
    val id: Int = ID
    val type: APIUserType = userType
    val token:String = accessToken
}