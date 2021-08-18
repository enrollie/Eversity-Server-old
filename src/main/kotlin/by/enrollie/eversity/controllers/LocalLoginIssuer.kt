/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.controllers

import java.security.MessageDigest
import java.util.*

class LocalLoginIssuer(private val logins: Map<String, Int>) {
    fun getUserID(username: String, password: String): Int? {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        return logins["$username-${Base64.getEncoder().encodeToString(messageDigest.digest(password.toByteArray()))}"]
    }
}