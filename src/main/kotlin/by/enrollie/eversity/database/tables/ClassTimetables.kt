package by.enrollie.eversity.database.tables

import org.jetbrains.exposed.sql.Table

object ClassTimetables: Table() {
    val id = (integer("classID") references Classes.classID)
    val monday = text("monday")
    val tuesday = text("tuesday")
    val wednesday = text("wednesday")
    val thursday = text("thursday")
    val friday = text("friday")
    val saturday = text("saturday")
}