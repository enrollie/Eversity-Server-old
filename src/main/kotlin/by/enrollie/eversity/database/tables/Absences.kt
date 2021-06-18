/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.jodatime.date

object Absences:Table() {
    val pupilID = (integer("pupilid") references Pupils.id)
    val classID = (integer("classid") references Classes.classID)
    val date = date("date")
    val reason = varchar("reason", 12)
}