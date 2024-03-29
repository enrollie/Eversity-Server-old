/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import by.enrollie.eversity.serializers.DateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.joda.time.DateTime

@Serializable
data class Absence(
    val pupilID: Int,
    val classID: Int,
    val sentByID: Int?,
    @Serializable(DateTimeSerializer::class)
    val date: DateTime,
    val reason: AbsenceReason,
    val lessonsList: List<Short>,
    val additionalNote: AbsenceNote?
)

@Serializable
enum class AbsenceNoteType { TEXT, ADDITIONAL_DATA }

interface AbsenceNote

@Serializable
@SerialName("TextAbsenceNote")
data class TextAbsenceNote(val message: String) : AbsenceNote

@Serializable
@SerialName("DataAbsenceNote")
data class DataAbsenceNote(
    val leftAftermath: Boolean? = null
) : AbsenceNote

val AbsenceNoteJSON = Json {
    serializersModule = SerializersModule {
        polymorphic(AbsenceNote::class) {
            subclass(DataAbsenceNote::class)
            subclass(TextAbsenceNote::class)
        }
    }
}
