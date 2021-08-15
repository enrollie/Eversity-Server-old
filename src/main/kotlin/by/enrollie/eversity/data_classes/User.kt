/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import by.enrollie.eversity.database.functions.doesUserExist
import kotlinx.serialization.Serializable

@Serializable
data class User(val id: Int, val type: APIUserType) {
    fun isValid(): Boolean =
        doesUserExist(id)

}