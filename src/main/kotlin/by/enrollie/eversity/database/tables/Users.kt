package by.enrollie.eversity.database.tables

import org.jetbrains.exposed.sql.Table

object Users:Table() {
    val id = (integer("id").uniqueIndex())
    val type = varchar("type", 15) //user's type. since i don't know, what will be, if director or anyone else login to Schools.by API, i've entered length of "administration" (14) + 1
}