package by.enrollie.eversity.data_classes

import kotlinx.serialization.Serializable

@Serializable
data class Mark(val id:Int, val markNum:Short, val pupil: Pupil)