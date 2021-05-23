package by.enrollie.eversity.database.tables

import org.jetbrains.exposed.sql.Table

object Marks: Table() {
    val id = integer("id")
    val pupilID = (integer("pupilid") references Pupils.id)
    val classID = (integer("classid") references Classes.classID)
    val date = varchar("date", 10)
    val timestamp = long("timestamp")
    val journalID = integer("journalid")
    val lessonID = integer("lessonid")
}