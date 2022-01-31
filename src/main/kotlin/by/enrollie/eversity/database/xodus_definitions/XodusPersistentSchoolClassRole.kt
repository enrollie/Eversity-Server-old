/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.xodus_definitions

import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.*
import kotlinx.dnq.link.OnDeletePolicy

class XodusPersistentSchoolClassRoleType(entity: Entity) : XdEnumEntity(entity) {
    companion object : XdEnumEntityType<XodusPersistentSchoolClassRoleType>() {
        val CLASS_TEACHER by enumField {
            title = "class_teacher"
        }
        val DATA_DELEGATE by enumField {
            title = "data_delegate"
        }
    }

    var title by xdRequiredStringProp(unique = true)
}

class XodusPersistentSchoolClassRole(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XodusPersistentSchoolClassRole>()

    var schoolClass by xdLink1(XodusClass, onTargetDelete = OnDeletePolicy.CLEAR)
    var role by xdLink1(XodusPersistentSchoolClassRoleType)
    var user: XodusUser by xdParent(XodusUser::persistentRoles)
}
