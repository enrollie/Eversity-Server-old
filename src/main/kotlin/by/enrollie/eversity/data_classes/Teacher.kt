package by.enrollie.eversity.data_classes

import kotlinx.serialization.Serializable

@Serializable
data class Teacher(
    val id: Int,
    val firstName: String,
    val middleName: String,
    val lastName: String,
    val speciality: String?
)
