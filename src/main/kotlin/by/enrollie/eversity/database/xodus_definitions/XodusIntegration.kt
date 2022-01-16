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

open class XodusIntegration(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XodusIntegration>()

    var user: XodusUser by xdParent(XodusUser::integrations)
    open val uuid: String
        get() = "empty-data"
    open val publicName: String
        get() = "Empty integration"
}
