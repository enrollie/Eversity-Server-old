/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.security

import by.enrollie.eversity.data_classes.APIUserType
import io.ktor.auth.*

class User:Principal {
    val id: Int
    val type: APIUserType
    constructor(ID:Int, userType: APIUserType){
        type = userType
        id = ID
    }
}