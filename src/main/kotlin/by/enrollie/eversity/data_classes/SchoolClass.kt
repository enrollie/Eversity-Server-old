/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import kotlinx.serialization.Serializable


@Serializable
data class SchoolClass(val id: Int, val title: String, val classTeacherID:Int, val pupils: Array<Pupil>) {
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

}
