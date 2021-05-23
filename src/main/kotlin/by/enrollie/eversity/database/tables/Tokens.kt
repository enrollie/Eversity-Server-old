/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.tables
import org.jetbrains.exposed.sql.Table

object Tokens: Table() {
    val userID = integer("userid")
    val token = varchar("token", 36)
}