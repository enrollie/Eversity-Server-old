/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import by.enrollie.eversity.database.xodus_definitions.XodusParentProfile
import by.enrollie.eversity.database.xodus_definitions.XodusUser
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.query.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
class Parent(
    override val id: Int,
    override val firstName: String,
    override val middleName: String?,
    override val lastName: String
) : User {
    @SerialName("userType")
    override val type: UserType = UserType.Parent

    fun getPupils(store: TransientEntityStore): Array<Pupil>? = store.transactional {
        XodusParentProfile.query(XodusParentProfile::user.matches(XodusUser::id eq id)).firstOrNull()?.pupils?.toList()
            ?.map {
                Pupil(it.user.id, it.user.firstName, it.user.middleName, it.user.lastName, it.schoolClass.id)
            }?.toTypedArray()
    }
}
