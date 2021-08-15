/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.data_classes.*
import by.enrollie.eversity.database.cachePupil
import by.enrollie.eversity.database.cacheTeacher
import by.enrollie.eversity.database.tables.*
import by.enrollie.eversity.database.validateDaysMap
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Registers class teacher (does not check for credentials validity).
 * (does not register pupils, neither timetables)
 *
 * @param userID User ID of class teacher
 * @param fullName [Triple] First - First name, Second - Middle name, Third - Last name
 * @param cookies [Pair] First - csrftoken, second - sessionID
 * @param tokenAPI Token for Schools.by API
 * @param classID ID of class
 *
 * @return False, if user already exists. True, if operation succeed
 */
fun registerTeacher(
    userID: Int,
    fullName: Triple<String, String, String>,
    cookies: Pair<String, String>,
    tokenAPI: String,
    classID: Int? = null
): Boolean {
    if (doesUserExist(userID)) {
        return false
    }
    registerUser(userID, APIUserType.Teacher.name)
    transaction {
        Teachers.insert {
            it[id] = userID
            it[firstName] = fullName.first
            it[middleName] = fullName.second
            it[lastName] = fullName.third
            it[Teachers.classID] = classID
        }
    }
    cacheTeacher(Teacher(userID, fullName.first, fullName.second, fullName.third), classID)
    insertOrUpdateCredentials(userID, Triple(cookies.first, cookies.second, tokenAPI))
    return true
}

/**
 * Registers single pupil (does not register it's timetable)
 *
 * @param userID ID of pupil
 * @param name First - First name, Second - Last name
 * @param classID Class ID of given pupil
 * @see registerManyPupils
 * @return False, if pupil already exists. True, if operation succeed
 * @throws IllegalArgumentException Thrown, if pupil's class is not yet registered.
 * @throws UnknownError Thrown, if registerUser() was called, but [doesUserExist] returned false (user does not exist)
 */
fun registerPupil(userID: Int, name: Pair<String, String>, classID: Int): Boolean {
    if (doesUserExist(userID)) {
        return false
    }
    if (!doesClassExist(classID)) {
        throw IllegalArgumentException("Class with ID $classID does not yet exist. Pupil with ID $userID cannot be registered.")
    }
    registerUser(userID, APIUserType.Pupil.name)
    transaction {
        Pupils.insert {
            it[id] = userID
            it[Pupils.classID] = classID
            it[firstName] = name.first
            it[lastName] = name.second
        }
    }
    cachePupil(Pupil(userID, name.first, name.second, classID))
    return true
}

/**
 * Registers many pupils at once. (does not register their timetables).
 * Ignores pupil if it already exists.
 * @param array Array, containing [Pupil]s to register
 */
fun registerManyPupils(array: Array<Pupil>) {
    val list = array.toList()
    transaction {
        Users.batchInsert(list, shouldReturnGeneratedValues = false, ignore = true) { pupil ->
            this[Users.id] = pupil.id
            this[Users.type] = APIUserType.Pupil.name
        }
        Pupils.batchInsert(list, shouldReturnGeneratedValues = false, ignore = true) { pupil ->
            this[Pupils.id] = pupil.id
            this[Pupils.firstName] = pupil.firstName
            this[Pupils.lastName] = pupil.lastName
            this[Pupils.classID] = pupil.classID
        }
    }
    list.forEach {
        cachePupil(it)
    }
}

/**
 * Registers (or, overwrites, if exists) timetable for class
 *
 * @param classID ID of pupil
 * @param daysMap Map of <[DayOfWeek], Array of [Lesson]>. MUST contain at least six elements (days) AND less than 8 elements
 *
 * @throws IllegalArgumentException Thrown, if [daysMap] does not contain some day of week (except SUNDAY) OR it has less than six elements
 */
fun registerClassTimetable(classID: Int, daysMap: Map<DayOfWeek, Array<Lesson>>) {
    require(validateDaysMap(daysMap)) { "Day map should be valid" }
    if (!doesClassExist(classID)) {
        throw IllegalArgumentException("Class with ID $classID does not exist")
    }
    transaction {
        ClassTimetables.deleteWhere {
            ClassTimetables.id eq classID
        }
        ClassTimetables.insert {
            it[id] = classID
            it[monday] = Json.encodeToString(daysMap[DayOfWeek.MONDAY])
            it[tuesday] = Json.encodeToString(daysMap[DayOfWeek.TUESDAY])
            it[wednesday] = Json.encodeToString(daysMap[DayOfWeek.WEDNESDAY])
            it[thursday] = Json.encodeToString(daysMap[DayOfWeek.THURSDAY])
            it[friday] = Json.encodeToString(daysMap[DayOfWeek.FRIDAY])
            it[saturday] = Json.encodeToString(daysMap[DayOfWeek.SATURDAY])
        }
    }
}

/**
 * Registers user
 */
private fun registerUser(userID: Int, type: String) {
    transaction {
        Users.insert {
            it[id] = userID
            it[Users.type] = type
        }
    }
}

/**
 * Registers parent
 * @param userID Parent ID
 */
fun registerParent(userID: Int) =
    if (!doesUserExist(userID)) registerUser(userID, APIUserType.Parent.name) else Unit

/**
 * Registers (or, updates) school class (does not register class teacher, pupils, timetables etc.)
 *
 * @param classID ID of class
 * @param classTeacherID ID of class teacher (does not need to be registered)
 */
fun registerClass(classID: Int, classTeacherID: Int, name: String, isSecondShift: Boolean) {
    if (doesClassExist(classID))
        return
    transaction {
        Classes.insert {
            it[Classes.classID] = classID
            it[classTeacher] = classTeacherID
            it[Classes.name] = name
            it[Classes.isSecondShift] = isSecondShift
        }
    }
    return
}

/**
 * Registers (or, overwrites, if exists) timetable for teacher
 *
 * @param teacherID ID of pupil
 * @param daysPair Pair of timetables in first-second shift order
 *
 * @throws IllegalArgumentException Thrown, if [daysPair] does not contain some day of week (except SUNDAY) OR it has less than six elements
 */
fun registerTeacherTimetable(
    teacherID: Int,
    daysPair: Pair<Map<DayOfWeek, Array<TeacherLesson>>?, Map<DayOfWeek, Array<TeacherLesson>>?>
) {
    if (daysPair.first != null) {
        if (!validateDaysMap(daysPair.first!!))
            throw IllegalArgumentException("First daysMap is not valid.")
    }
    if (daysPair.second != null) {
        if (!validateDaysMap(daysPair.second!!))
            throw IllegalArgumentException("Second daysMap is not valid.")
    }
    transaction {
        TeachersTimetable.deleteWhere {
            TeachersTimetable.id eq teacherID
        }
        TeachersTimetable.insert {
            it[id] = teacherID
            it[monday] = Json.encodeToString(
                Pair(
                    daysPair.first?.get(DayOfWeek.MONDAY),
                    daysPair.second?.get(DayOfWeek.MONDAY)
                )
            )
            it[tuesday] = Json.encodeToString(
                Pair(
                    daysPair.first?.get(DayOfWeek.TUESDAY),
                    daysPair.second?.get(DayOfWeek.TUESDAY)
                )
            )
            it[wednesday] = Json.encodeToString(
                Pair(
                    daysPair.first?.get(DayOfWeek.WEDNESDAY),
                    daysPair.second?.get(DayOfWeek.WEDNESDAY)
                )
            )
            it[thursday] = Json.encodeToString(
                Pair(
                    daysPair.first?.get(DayOfWeek.THURSDAY),
                    daysPair.second?.get(DayOfWeek.THURSDAY)
                )
            )
            it[friday] = Json.encodeToString(
                Pair(
                    daysPair.first?.get(DayOfWeek.FRIDAY),
                    daysPair.second?.get(DayOfWeek.FRIDAY)
                )
            )
            it[saturday] = Json.encodeToString(
                Pair(
                    daysPair.first?.get(DayOfWeek.SATURDAY),
                    daysPair.second?.get(DayOfWeek.SATURDAY)
                )
            )
        }
    }
}

fun registerAdministration(
    userID: Int,
    fullName: Triple<String, String, String>,
    cookies: Pair<String, String>,
    tokenAPI: String
): Boolean {
    if (doesUserExist(userID)) {
        return false
    }
    registerUser(userID, APIUserType.Administration.name)
    transaction {
        Teachers.insert {
            it[id] = userID
            it[firstName] = fullName.first
            it[middleName] = fullName.second
            it[lastName] = fullName.third
            it[classID] = null
        }
    }
    cacheTeacher(Teacher(userID, fullName.first, fullName.second, fullName.third), null)
    insertOrUpdateCredentials(userID, Triple(cookies.first, cookies.second, tokenAPI))
    return true
}