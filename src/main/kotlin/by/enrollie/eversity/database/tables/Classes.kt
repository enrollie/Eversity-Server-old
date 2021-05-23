package by.enrollie.eversity.database.tables

import org.jetbrains.exposed.sql.Table

object Classes:Table() {
    val classID = (integer("classid").uniqueIndex())
    val classTeacher = integer("classteacher")
}