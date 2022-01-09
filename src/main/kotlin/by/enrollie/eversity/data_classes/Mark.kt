/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import kotlinx.serialization.Serializable

@Serializable
data class Mark(val id: Int, val markNum: Short?, val lessonPlace: Short, val pupil: Pupil)
