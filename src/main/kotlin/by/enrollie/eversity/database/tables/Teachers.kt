package by.enrollie.eversity.database.tables

import org.jetbrains.exposed.sql.Table

object Teachers:Table() {
    val id = (integer("teacherid") references Users.id).uniqueIndex()
    val firstName = varchar("firstname", 50)
    val middleName = varchar("middlename", 50)
    val lastName = varchar("lastname", 50)

    val classID = (integer("classid") references Classes.classID).nullable() //if teacher is a class teacher, this row will have classID, else null
}