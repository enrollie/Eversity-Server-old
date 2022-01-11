/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.xodus_definitions

import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.XdEntity
import kotlinx.dnq.XdNaturalEntityType
import kotlinx.dnq.xdParent
import kotlinx.dnq.xdRequiredLongProp

class XodusTelegramData(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XodusTelegramData>()

    var parentProfile: XodusParentProfile by xdParent(XodusParentProfile::telegramData)
    var telegramChatID by xdRequiredLongProp { }
}
