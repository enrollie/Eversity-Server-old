/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.serializers

import by.enrollie.eversity.data_classes.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

private val module = SerializersModule {
    polymorphic(User::class) {
        subclass(Parent::class)
        subclass(Pupil::class)
        subclass(Administration::class)
        subclass(Teacher::class)
    }
}

val UsersJSON = Json {
    serializersModule += module
}
