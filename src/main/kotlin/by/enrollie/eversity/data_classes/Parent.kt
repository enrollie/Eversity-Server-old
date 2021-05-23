package by.enrollie.eversity.data_classes

import kotlinx.serialization.Serializable


@Serializable
data class Parent(val id:Int, val firstName:String, val middleName:String, val secondName:String, val pupils: Array<Pupil>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Parent

        if (id != other.id) return false
        if (firstName != other.firstName) return false
        if (middleName != other.middleName) return false
        if (secondName != other.secondName) return false
        if (!pupils.contentEquals(other.pupils)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + firstName.hashCode()
        result = 31 * result + middleName.hashCode()
        result = 31 * result + secondName.hashCode()
        result = 31 * result + pupils.contentHashCode()
        return result
    }
}
