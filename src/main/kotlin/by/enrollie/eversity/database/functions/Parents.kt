/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.DATABASE
import by.enrollie.eversity.database.xodus_definitions.XodusParentProfile
import by.enrollie.eversity.database.xodus_definitions.XodusUser
import by.enrollie.eversity.database.xodus_definitions.toPupilsArray
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.query.*

/**
 * Returns parent's registered
 */
fun getParentsPupils(parentID: Int, store: TransientEntityStore = DATABASE) = store.transactional(readonly = true) {
    XodusParentProfile.query(XodusParentProfile::user.matches(XodusUser::id eq parentID)).first().pupils.toList()
        .toPupilsArray()
}
