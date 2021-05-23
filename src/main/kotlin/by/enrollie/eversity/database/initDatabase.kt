package by.enrollie.eversity.database

import by.enrollie.eversity.database.tables.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Connects to Eversity database and creates all missing tables.
 * Uses PostgreSQL.
 * @param host Database host
 * @param port Database port
 * @param databaseName Database name
 * @param user Database user
 * @param password Database password
 */

fun initDatabase(
    host: String,
    port: String,
    databaseName: String,
    user: String,
    password: String
) {
    val tables = arrayOf(
        Classes,
        Absence,
        ClassTimetables,
        Credentials,
        Pupils,
        BannedTokens,
        Teachers,
        TeachersTimetable,
        Tokens,
        Users,
        Marks
    )

    Database.connect(
        url = "jdbc:postgresql://$host:$port/$databaseName",
        driver = "org.postgresql.Driver",
        user = user,
        password = password
    )

    transaction {
        SchemaUtils.createMissingTablesAndColumns(*tables)
    }
}