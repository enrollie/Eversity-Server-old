/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.tables

import org.jetbrains.exposed.sql.Table

object Marks: Table() {
    val id = integer("id").uniqueIndex()
    val value = short("markvalue").nullable()
    val pupilID = (integer("pupilid") references Pupils.id)
    val classID = (integer("classid") references Classes.classID)
    val date = varchar("date", 10)
    val place = short("lessonplace")
    val journalID = integer("journalid")
    val lessonID = integer("lessonid")
}