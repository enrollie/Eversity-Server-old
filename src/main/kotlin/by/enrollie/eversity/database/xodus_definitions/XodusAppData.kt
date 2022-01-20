/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.xodus_definitions

import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.XdEntity
import kotlinx.dnq.simple.min
import kotlinx.dnq.singleton.XdSingletonEntityType
import kotlinx.dnq.xdRequiredDateTimeProp
import kotlinx.dnq.xdRequiredIntProp
import org.joda.time.DateTime

class XodusAppData(entity: Entity) : XdEntity(entity) {
    companion object : XdSingletonEntityType<XodusAppData>() {
        override fun XodusAppData.initSingleton() {
            modelVersion = 1
            firstInitDate = DateTime.now()
        }
    }

    var modelVersion by xdRequiredIntProp { min(1, "Version cannot be less than 1") }
    var firstInitDate by xdRequiredDateTimeProp { }
}
