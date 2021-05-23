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