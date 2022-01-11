/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.serializers

import by.enrollie.eversity.data_classes.TimeConstraints
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*

class TimeConstraintsSerializer : KSerializer<TimeConstraints> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("TimeConstraints") {
            element<Short>("startH")
            element<Short>("startM")
            element<Short>("endH")
            element<Short>("endM")
        }

    override fun serialize(encoder: Encoder, value: TimeConstraints) {
        encoder.encodeStructure(descriptor) {
            encodeShortElement(descriptor, 0, value.startHour)
            encodeShortElement(descriptor, 1, value.startMinute)
            encodeShortElement(descriptor, 2, value.endHour)
            encodeShortElement(descriptor, 3, value.endMinute)
        }
    }

    override fun deserialize(decoder: Decoder): TimeConstraints =
        decoder.decodeStructure(descriptor) {
            var startHour: Short = -1
            var startMinute: Short = -1
            var endHour: Short = -1
            var endMinute: Short = -1
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> startHour = decodeShortElement(descriptor, index)
                    1 -> startMinute = decodeShortElement(descriptor, index)
                    2 -> endHour = decodeShortElement(descriptor, index)
                    3 -> endMinute = decodeShortElement(descriptor, index)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            require(startHour in 0..24 && startMinute in 0..60 && endHour in 0..24 && endMinute in 0..60)
            TimeConstraints(startHour, startMinute, endHour, endMinute)
        }
}
