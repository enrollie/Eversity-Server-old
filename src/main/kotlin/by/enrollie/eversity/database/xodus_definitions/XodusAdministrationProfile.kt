/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.xodus_definitions

import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.XdNaturalEntityType

class XodusAdministrationProfile(entity: Entity) : XodusTeacherProfile(entity) {
    companion object : XdNaturalEntityType<XodusAdministrationProfile>() {
        override fun new(init: XodusAdministrationProfile.() -> Unit): XodusAdministrationProfile {
            return super.new(init).apply { this.type = XodusUserType.ADMINISTRATION }
        }
    }
}

