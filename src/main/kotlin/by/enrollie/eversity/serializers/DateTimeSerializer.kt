/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.joda.time.DateTime
import java.text.SimpleDateFormat

private const val parsePattern = "YYYY-MM-dd"

class DateTimeSerializer : KSerializer<DateTime> {
    private val formatter = SimpleDateFormat(parsePattern)
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DateTime) {
        encoder.encodeString(value.toString("YYYY-MM-dd"))
    }

    override fun deserialize(decoder: Decoder): DateTime = DateTime(formatter.parse(decoder.decodeString()).time)
}
