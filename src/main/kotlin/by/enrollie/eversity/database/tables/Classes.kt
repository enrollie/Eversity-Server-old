/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.tables

import org.jetbrains.exposed.sql.Table

object Classes:Table() {
    val classID = (integer("classid").uniqueIndex())
    val classTeacher = integer("classteacher")
    val name = varchar("name", 10)
    val isSecondShift = bool("issecondshift")
}