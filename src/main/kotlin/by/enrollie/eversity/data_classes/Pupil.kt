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
data class Pupil(
    override val id: Int,
    override val firstName: String,
    override val middleName: String?,
    override val lastName: String,
    val classID: Int,
) : User {
    @SerialName("userType")
    override val type: UserType = UserType.Pupil
}

fun List<com.neitex.Pupil>.toPupilsList(): List<Pupil> =
    this.map { Pupil(it.id, it.name.firstName, it.name.middleName, it.name.lastName, it.classID) }
