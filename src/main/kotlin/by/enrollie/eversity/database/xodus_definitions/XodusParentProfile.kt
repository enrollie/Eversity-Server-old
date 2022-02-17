/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.xodus_definitions

import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.XdNaturalEntityType
import kotlinx.dnq.xdLink0_N

class XodusParentProfile(entity: Entity) : XodusBaseUserProfile(entity) {
    companion object : XdNaturalEntityType<XodusParentProfile>() {
        override fun new(init: XodusParentProfile.() -> Unit): XodusParentProfile {
            return super.new(init).apply {
                type = XodusUserType.TEACHER
            }
        }
    }

    val pupils by xdLink0_N(XodusPupilProfile)
}
