/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.xodus_definitions

import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.*

class XodusToken(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XodusToken>()

    var token by xdRequiredStringProp { }
    var user: XodusUser by xdParent(XodusUser::accessTokens)
    var issueDate by xdRequiredDateTimeProp {}
    var invalid by xdBooleanProp { }
    var deviceOS by xdStringProp { }
}
