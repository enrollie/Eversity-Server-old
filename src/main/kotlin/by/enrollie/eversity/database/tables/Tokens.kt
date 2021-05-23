package by.enrollie.eversity.database.tables
import org.jetbrains.exposed.sql.Table

object Tokens: Table() {
    val userID = (integer("userid") references Users.id)
    val token = varchar("token", 36)
}