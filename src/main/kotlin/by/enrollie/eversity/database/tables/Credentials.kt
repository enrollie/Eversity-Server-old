/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.tables

import org.jetbrains.exposed.sql.Table

object Credentials: Table() {
    val id = (integer("id") references Users.id)
    val csrfToken = varchar("csrftoken", 32).nullable()
    val sessionID = varchar("sessionID", 32).nullable()
    val token = varchar("token", 32)
}