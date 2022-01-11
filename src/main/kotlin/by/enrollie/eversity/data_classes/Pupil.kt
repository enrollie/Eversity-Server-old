/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import by.enrollie.eversity.database.xodus_definitions.XodusPupilProfile
import by.enrollie.eversity.database.xodus_definitions.XodusUser
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.query.eq
import kotlinx.dnq.query.firstOrNull
import kotlinx.dnq.query.matches
import kotlinx.dnq.query.query
import kotlinx.serialization.Serializable

@Serializable
class Pupil(
    override val id: Int,
    override val firstName: String,
    override val middleName: String?,
    override val lastName: String,
    val classID: Int
) : User {
    override val type: UserType = UserType.Pupil

    companion object {
        fun fromUserData(store: TransientEntityStore, user: User): Pupil? {
            check(user.type == UserType.Pupil)
            val classID = store.transactional {
                XodusPupilProfile.query(XodusPupilProfile::user.matches(XodusUser::id eq user.id))
                    .firstOrNull()?.schoolClass?.id
            } ?: return null
            return Pupil(user.id, user.firstName, user.middleName, user.lastName, classID)
        }
    }
}

fun List<com.neitex.Pupil>.toPupilsList(): List<Pupil> =
    this.map { Pupil(it.id, it.name.firstName, it.name.middleName, it.name.lastName, it.classID) }
