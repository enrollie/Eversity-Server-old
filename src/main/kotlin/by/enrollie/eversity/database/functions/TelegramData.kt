/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.DATABASE
import by.enrollie.eversity.database.xodus_definitions.XodusParentProfile
import by.enrollie.eversity.database.xodus_definitions.XodusPupilProfile
import by.enrollie.eversity.database.xodus_definitions.XodusTelegramData
import by.enrollie.eversity.database.xodus_definitions.XodusUser
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.creator.findOrNew
import kotlinx.dnq.query.*

fun insertTelegramNotifyData(
    parentID: Int,
    telegramChatID: Long,
    store: TransientEntityStore = DATABASE
) = store.transactional {
    XodusTelegramData.query(XodusTelegramData::telegramChatID eq telegramChatID).firstOrNull()?.delete()
    XodusTelegramData.findOrNew {
        parentProfile = XodusParentProfile.query(XodusParentProfile::user.matches(XodusUser::id eq parentID)).first()
    }.apply {
        this.telegramChatID = telegramChatID
    }
}

fun getTelegramNotifyList(pupilID: Int, store: TransientEntityStore = DATABASE): List<Long> =
    store.transactional(readonly = true) {
        XodusParentProfile.query(
            XodusParentProfile::pupils.contains(
                XodusPupilProfile.query(
                    XodusPupilProfile::user.matches(
                        XodusUser::id eq pupilID
                    )
                ).first()
            )
        ).toList().mapNotNull { it.telegramData?.telegramChatID }
    }

fun removeTelegramNotifyData(userID: Int, store: TransientEntityStore = DATABASE) = store.transactional {
    XodusParentProfile.query(XodusParentProfile::user.matches(XodusUser::id eq userID))
        .firstOrNull()?.telegramData?.delete()
}

fun isRegisteredChat(chatID: Long, store: TransientEntityStore = DATABASE) = store.transactional(readonly = true) {
    XodusTelegramData.query(XodusTelegramData::telegramChatID eq chatID).isNotEmpty
}
