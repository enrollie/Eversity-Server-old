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
}
