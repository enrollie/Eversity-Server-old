/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.tables

import org.jetbrains.exposed.sql.Table

object Users:Table() {
    val id = (integer("id").uniqueIndex())
    val type = varchar(
        "type",
        15
    ) //user's type. since I don't know what will be, if director or anyone else login to Schools.by API, I've entered length of "administration" (14) + 1
}