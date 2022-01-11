/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import com.neitex.SchoolsByUserType
import kotlinx.serialization.Serializable

@Serializable
enum class UserType { Teacher, Parent, Pupil, SYSTEM, Social, Administration }

fun SchoolsByUserType.toUserType(): UserType = when (this) {
    SchoolsByUserType.PUPIL -> UserType.Pupil
    SchoolsByUserType.PARENT -> UserType.Parent
    SchoolsByUserType.TEACHER -> UserType.Teacher
    SchoolsByUserType.ADMINISTRATION -> UserType.Administration
}
