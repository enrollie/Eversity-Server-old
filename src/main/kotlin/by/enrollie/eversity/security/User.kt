/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.security

import by.enrollie.eversity.data_classes.User
import io.ktor.server.auth.*

data class User(val user: User, val accessToken: String) : Principal
