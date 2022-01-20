/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.xodus_definitions

import by.enrollie.eversity.data_classes.AbsenceNoteType
import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.*

class XodusAbsenceNoteType(entity: Entity) : XdEnumEntity(entity) {
    companion object : XdEnumEntityType<XodusAbsenceNoteType>() {
        val TEXT by enumField { title = "TEXT" }
        val ADDITIONAL_DATA by enumField { title = "ADDITIONAL_DATA" }
        fun XodusAbsenceNoteType.toEnum() = AbsenceNoteType.valueOf(this.title)
    }

    var title by xdRequiredStringProp(unique = true) { }
}

class XodusAbsenceNotes(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XodusAbsenceNotes>()

    var noteType by xdLink1(XodusAbsenceNoteType)
    var note by xdRequiredStringProp { }
}
