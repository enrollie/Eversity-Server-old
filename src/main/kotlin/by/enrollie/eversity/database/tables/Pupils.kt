/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.tables

import org.jetbrains.exposed.sql.Table

object Pupils: Table() {
    val id = (integer("id") references Users.id).uniqueIndex()
    val firstName = varchar("firstname", 50)
    val lastName = varchar("lastname", 50)
    val classID = (integer("classid") references Classes.classID)
}