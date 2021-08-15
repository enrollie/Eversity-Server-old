/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.database.tables.TelegramNotifyData
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

fun insertTelegramNotifyData(parentID: Int, pupilsIDList: List<Int>, telegramChatID: Long) {
    require(doesUserExist(parentID)) { "Parent with ID $parentID does not exist" }
    pupilsIDList.forEach { pupilID ->
        require(doesUserExist(pupilID)) { "Pupil with ID $pupilID does not exist" }
    }
    transaction {
        TelegramNotifyData.deleteWhere { TelegramNotifyData.telegramChatID eq telegramChatID }
        TelegramNotifyData.batchInsert(pupilsIDList, ignore = true, shouldReturnGeneratedValues = false) {
            this[TelegramNotifyData.parentID] = parentID
            this[TelegramNotifyData.telegramChatID] = telegramChatID
            this[TelegramNotifyData.pupilID] = it
        }
    }
}

fun getTelegramNotifyList(pupilID: Int): List<Long> {
    require(doesUserExist(pupilID)) { "Pupil with ID $pupilID does not exist" }
    return transaction {
        TelegramNotifyData.select {
            TelegramNotifyData.pupilID eq pupilID
        }.toList().map {
            it[TelegramNotifyData.telegramChatID]
        }
    }
}

fun removeTelegramNotifyData(userID: Int) {
    transaction {
        TelegramNotifyData.deleteWhere {
            TelegramNotifyData.parentID eq userID
        }
    }
}

fun isRegisteredChat(chatID: Long): Boolean =
    transaction { TelegramNotifyData.select { TelegramNotifyData.telegramChatID eq chatID }.toList().isNotEmpty() }