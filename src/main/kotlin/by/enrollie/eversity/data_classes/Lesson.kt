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
data class Lesson(
    val place: Int,
    val title: String,
    @SerialName("classId")
    val classID: Int,
    val schedule: TimeConstraints,
)

fun Array<com.neitex.Lesson>.toLessons(): Array<Lesson> = this.map {
    Lesson(it.place.toInt(), it.title, it.classID, it.timeConstraints.toTimeConstraints())
}.toTypedArray()
