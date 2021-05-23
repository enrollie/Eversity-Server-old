/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import kotlinx.serialization.*

@Serializable
data class Pupil(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val classID: Int
//    val parentsID: Pair<Int?, Int?>
)
