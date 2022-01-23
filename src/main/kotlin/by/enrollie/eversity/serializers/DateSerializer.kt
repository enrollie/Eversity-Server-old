/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.serializers

import by.enrollie.eversity.DATE_FORMAT
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.joda.time.DateTime
import org.joda.time.LocalTime
import org.joda.time.format.DateTimeFormat

class DateSerializer : KSerializer<DateTime> {
    private val formatter = DateTimeFormat.forPattern(DATE_FORMAT)
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DateTime) {
        encoder.encodeString(formatter.print(value))
    }

    override fun deserialize(decoder: Decoder): DateTime =
        formatter.parseDateTime(decoder.decodeString()).withTime(LocalTime.MIDNIGHT)
}
