/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import kotlinx.serialization.Serializable


@Serializable
data class SchoolClass(val number: Short, val letter:Char, val schoolsURL: String, val classTeacherID:Int, val pupils: Array<Pupil>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SchoolClass

        if (number != other.number) return false
        if (letter != other.letter) return false
        if (schoolsURL != other.schoolsURL) return false
        if (classTeacherID != other.classTeacherID) return false
        if (!pupils.contentEquals(other.pupils)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = number.toInt()
        result = 31 * result + letter.hashCode()
        result = 31 * result + schoolsURL.hashCode()
        result = 31 * result + classTeacherID
        result = 31 * result + pupils.contentHashCode()
        return result
    }
}
