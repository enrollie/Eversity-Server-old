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
import kotlinx.dnq.xdLink1
import kotlinx.dnq.xdParent

open class XodusBaseUserProfile(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XodusBaseUserProfile>()

    var type by xdLink1(XodusUserType)
    var user: XodusUser by xdParent(XodusUser::profile)
}
