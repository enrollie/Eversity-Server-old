package by.enrollie.eversity.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.jodatime.date

object BannedTokens: Table() {
    val userID = (integer("userid") references Users.id)
    val token = varchar("token", 36)
    val reason = text("banreason")
    val banDate = date("bandate")
}