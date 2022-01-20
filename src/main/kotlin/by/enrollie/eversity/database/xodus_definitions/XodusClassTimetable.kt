/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.xodus_definitions

import by.enrollie.eversity.data_classes.Timetable
import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.*
import kotlinx.dnq.link.OnDeletePolicy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.joda.time.DateTime

class XodusClassTimetable(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XodusClassTimetable>() {
        override fun new(init: XodusClassTimetable.() -> Unit): XodusClassTimetable {
            return super.new(init).also { it.validFrom = DateTime.now().withTimeAtStartOfDay() }
        }
    }

    var schoolClass by xdLink1(
        XodusClass::timetable,
        onTargetDelete = OnDeletePolicy.CASCADE,
        onDelete = OnDeletePolicy.CLEAR
    )
    var monday by xdRequiredStringProp { }
    var tuesday by xdRequiredStringProp { }
    var wednesday by xdRequiredStringProp { }
    var thursday by xdRequiredStringProp { }
    var friday by xdRequiredStringProp { }
    var saturday by xdRequiredStringProp { }
    var validFrom by xdRequiredDateTimeProp { }
        private set

    fun toTimetable(): Timetable = Timetable(
        Json.decodeFromString(monday),
        Json.decodeFromString(tuesday),
        Json.decodeFromString(wednesday),
        Json.decodeFromString(thursday),
        Json.decodeFromString(friday),
        Json.decodeFromString(saturday)
    )
}
