/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.tables

import org.jetbrains.exposed.sql.Table

object  TeachersTimetable: Table() {
    val id = (integer("id") references Teachers.id).uniqueIndex()
    val monday = text("monday")
    val tuesday = text("tuesday")
    val wednesday = text("wednesday")
    val thursday = text("thursday")
    val friday = text("friday")
    val saturday = text("saturday")
}