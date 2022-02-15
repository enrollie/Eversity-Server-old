/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Teacher(
    override val id: Int,
    override val firstName: String,
    override val middleName: String?,
    override val lastName: String,
) : User {
    @SerialName("userType")
    override val type: UserType = UserType.Teacher
}

@Serializable
class Administration(
    override val id: Int,
    override val firstName: String,
    override val middleName: String?,
    override val lastName: String,
) : User {
    @SerialName("userType")
    override val type: UserType = UserType.Administration

    override fun equals(other: Any?): Boolean {
        if (other !is Administration && other !is Teacher)
            return false
        if (other is Teacher)
            return other.id == id && other.firstName == firstName && other.middleName == middleName && other.lastName == lastName && other.type == type
        other as Administration
        return other.id == id && other.firstName == firstName && other.middleName == middleName && other.lastName == lastName && other.type == type
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + firstName.hashCode()
        result = 31 * result + (middleName?.hashCode() ?: 0)
        result = 31 * result + lastName.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }
}
