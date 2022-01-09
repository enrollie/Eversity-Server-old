/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import by.enrollie.eversity.database.xodus_definitions.XodusClass
import by.enrollie.eversity.database.xodus_definitions.XodusTeacherProfile
import by.enrollie.eversity.database.xodus_definitions.XodusUser
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.query.*
import kotlinx.serialization.Serializable

@Serializable
class Teacher(
    override val id: Int,
    override val firstName: String,
    override val middleName: String?,
    override val lastName: String
) : User {
    override val type: UserType = UserType.Teacher

    fun teacherClass(store: TransientEntityStore) = store.transactional {
        XodusClass.query((XodusClass::classTeacher).matches(XodusTeacherProfile::user.matches(XodusUser::id eq this.id)))
            .firstOrNull()?.let {
                SchoolClass(it.id, it.classTitle, it.isSecondShift, it.classTeacher.user.id, it.pupils.toList().map {
                    Pupil(it.user.id, it.user.firstName, it.user.middleName, it.user.lastName, it.schoolClass.id)
                }.toTypedArray())
            }
    }

    companion object {
        fun fromUserData(store: TransientEntityStore, user: User): Teacher {
            check(user.type == UserType.Teacher)
            return Teacher(user.id, user.firstName, user.middleName, user.lastName)
        }
    }
}
