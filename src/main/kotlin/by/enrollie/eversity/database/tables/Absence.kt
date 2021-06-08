/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

@file:Suppress("unused", "unused")

package by.enrollie.eversity.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.jodatime.datetime

object Absence:Table() {
    val pupilID = (integer("pupilid") references Pupils.id)
    val classID = (integer("classid") references Classes.classID)
    val date = datetime("date")
    val insertedDate = datetime("insertdate")
    val teacherID = (integer("teacherid") references Teachers.id)
}