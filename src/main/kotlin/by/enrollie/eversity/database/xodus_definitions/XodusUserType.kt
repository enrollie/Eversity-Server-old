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
        val PARENT by enumField { title = "Parent" }
        val PUPIL by enumField { title = "Pupil" }
        val TEACHER by enumField { title = "Teacher" }
        val SOCIAL_TEACHER by enumField { title = "Social" }
        val ADMINISTRATION by enumField { title = "Administration" }
        val SYSTEM by enumField { title = "SYSTEM" }
    }

    var title by xdRequiredStringProp(unique = true)

    fun toEnum(): UserType = UserType.valueOf(title)
}
