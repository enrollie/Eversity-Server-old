/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import by.enrollie.eversity.database.xodus_definitions.XodusClass
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.query.eq
import kotlinx.dnq.query.firstOrNull
import kotlinx.dnq.query.query
import kotlinx.dnq.query.toList
import kotlinx.serialization.Serializable


@Serializable
data class SchoolClass(
    val id: Int,
    val title: String,
    val isSecondShift: Boolean,
    val classTeacherID: Int,
    val pupils: Array<Pupil>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SchoolClass

        if (title != other.title) return false
        if (classTeacherID != other.classTeacherID) return false
        if (!pupils.contentEquals(other.pupils)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + title.hashCode()
        result = 31 * result + classTeacherID
        result = 31 * result + pupils.contentHashCode()
        return result
    }

    companion object {
        fun getClassByID(store: TransientEntityStore, id: Int) = store.transactional {
            XodusClass.query(XodusClass::id eq id).firstOrNull()?.let {
                SchoolClass(it.id, it.classTitle, it.isSecondShift, it.classTeacher.user.id, it.pupils.toList().map {
                    Pupil(it.user.id, it.user.firstName, it.user.middleName, it.user.lastName, it.schoolClass.id)
                }.toTypedArray())
            }
        }
    }
}
