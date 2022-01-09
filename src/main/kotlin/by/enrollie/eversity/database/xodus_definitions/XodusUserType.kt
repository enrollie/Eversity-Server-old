/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.xodus_definitions

import by.enrollie.eversity.data_classes.UserType
import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.XdEnumEntity
import kotlinx.dnq.XdEnumEntityType
import kotlinx.dnq.xdRequiredStringProp

class XodusUserType(entity: Entity) : XdEnumEntity(entity) {
    companion object : XdEnumEntityType<XodusUserType>() {
        val PARENT by enumField { title = "PARENT" }
        val PUPIL by enumField { title = "PUPIL" }
        val TEACHER by enumField { title = "TEACHER" }
        val SOCIAL_TEACHER by enumField { title = "SOCIAL_TEACHER" }
        val ADMINISTRATION by enumField { title = "ADMINISTRATION" }
        val SYSTEM by enumField { title = "SYSTEM" }
    }

    var title by xdRequiredStringProp(unique = true)

    fun toEnum(): UserType = UserType.valueOf(title)
}
