/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.tables

import org.jetbrains.exposed.sql.Table

object TelegramNotifyData: Table() {
    val parentID = integer("parentid")
    val pupilID = integer("pupilid")
    val telegramChatID = long("telegramchatid")
}