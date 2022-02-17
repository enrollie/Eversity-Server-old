/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity_plugins.plugin_api

data class User(
    val id: Int,
    val firstName: String,
    val middleName: String?,
    val lastName: String
)
