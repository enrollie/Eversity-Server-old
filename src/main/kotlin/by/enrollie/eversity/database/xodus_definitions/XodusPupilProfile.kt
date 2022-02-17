/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.xodus_definitions

import by.enrollie.eversity.data_classes.Pupil
import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.XdNaturalEntityType
import kotlinx.dnq.link.OnDeletePolicy
import kotlinx.dnq.xdLink1

class XodusPupilProfile(entity: Entity) : XodusBaseUserProfile(entity) {
    companion object : XdNaturalEntityType<XodusPupilProfile>() {
        override fun new(init: XodusPupilProfile.() -> Unit): XodusPupilProfile {
            return super.new(init).apply {
                type = XodusUserType.PUPIL
            }
        }
    }

    var schoolClass by xdLink1(
        XodusClass::pupils,
        onTargetDelete = OnDeletePolicy.CASCADE,
        onDelete = OnDeletePolicy.CLEAR
    )
}

fun List<XodusPupilProfile>.toPupilsArray(): Array<Pupil> = this.map {
    Pupil(it.user.id, it.user.firstName, it.user.middleName, it.user.lastName, it.schoolClass.id)
}.sortedBy { "${it.lastName} ${it.firstName}" }.toTypedArray()
