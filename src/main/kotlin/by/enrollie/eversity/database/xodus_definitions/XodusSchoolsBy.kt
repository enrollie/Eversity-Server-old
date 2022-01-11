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
import kotlinx.dnq.xdRequiredStringProp

class XodusSchoolsBy(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XodusSchoolsBy>()

    var user: XodusUser by xdParent(XodusUser::schoolsByCredentials)
    var csrfToken by xdRequiredStringProp { }
    var sessionID by xdRequiredStringProp { }
}
