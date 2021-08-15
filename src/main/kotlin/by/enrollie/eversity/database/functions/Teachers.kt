/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.data_classes.APIUserType
import by.enrollie.eversity.data_classes.DayOfWeek
import by.enrollie.eversity.data_classes.Teacher
import by.enrollie.eversity.data_classes.TeacherLesson
import by.enrollie.eversity.database.cacheTeacher
import by.enrollie.eversity.database.findCachedTeacher
import by.enrollie.eversity.database.tables.Teachers
import by.enrollie.eversity.database.tables.TeachersTimetable
import by.enrollie.eversity.exceptions.UserNotRegistered
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Returns teacher's timetable
 * @param userID Teacher's ID
 * @return Map, containing six arrays of lessons (may be unsorted), mapped to six [DayOfWeek]'s (from Monday to Saturday)
 */
fun getTeacherTimetable(userID: Int): Pair<Map<DayOfWeek, Array<TeacherLesson>>?, Map<DayOfWeek, Array<TeacherLesson>>?> {
    if (!doesUserExist(userID)) {
        throw UserNotRegistered("User with ID $userID does not exist")
    }
    if (getUserType(userID) != APIUserType.Teacher)
        throw IllegalArgumentException("User with ID $userID is not a Teacher (they are ${getUserType(userID).name}, in fact)")
    val timetable = transaction {
        TeachersTimetable.select {
            TeachersTimetable.id eq userID
        }.toList().firstOrNull()
    }
        ?: throw NoSuchElementException("Timetable of teacher with ID $userID was not found. Most likely, database bad times are coming")
    var firstShiftTimetableMap: Map<DayOfWeek, Array<TeacherLesson>>? = mutableMapOf()
    var secondShiftTimetableMap: Map<DayOfWeek, Array<TeacherLesson>>? =
        mutableMapOf()
    //Danger: big chunk of code incoming
    val mondayPair =
        Json.decodeFromString<Pair<Array<TeacherLesson>?, Array<TeacherLesson>?>>(timetable[TeachersTimetable.monday])
    val tuesdayPair =
        Json.decodeFromString<Pair<Array<TeacherLesson>?, Array<TeacherLesson>?>>(timetable[TeachersTimetable.tuesday])
    val wednesdayPair =
        Json.decodeFromString<Pair<Array<TeacherLesson>?, Array<TeacherLesson>?>>(timetable[TeachersTimetable.wednesday])
    val thursdayPair =
        Json.decodeFromString<Pair<Array<TeacherLesson>?, Array<TeacherLesson>?>>(timetable[TeachersTimetable.thursday])
    val fridayPair =
        Json.decodeFromString<Pair<Array<TeacherLesson>?, Array<TeacherLesson>?>>(timetable[TeachersTimetable.friday])
    val saturdayPair =
        Json.decodeFromString<Pair<Array<TeacherLesson>?, Array<TeacherLesson>?>>(timetable[TeachersTimetable.saturday])
    if (mondayPair.first == null)
        firstShiftTimetableMap = null
    if (mondayPair.second == null)
        secondShiftTimetableMap = null
    if (firstShiftTimetableMap == null && secondShiftTimetableMap == null)
        return Pair(null, null)
    if (firstShiftTimetableMap != null) {
        firstShiftTimetableMap = mutableMapOf()
        firstShiftTimetableMap[DayOfWeek.MONDAY] = mondayPair.first!!
        firstShiftTimetableMap[DayOfWeek.TUESDAY] = tuesdayPair.first!!
        firstShiftTimetableMap[DayOfWeek.WEDNESDAY] = wednesdayPair.first!!
        firstShiftTimetableMap[DayOfWeek.THURSDAY] = thursdayPair.first!!
        firstShiftTimetableMap[DayOfWeek.FRIDAY] = fridayPair.first!!
        firstShiftTimetableMap[DayOfWeek.SATURDAY] = saturdayPair.first!!
    }
    if (secondShiftTimetableMap != null) {
        secondShiftTimetableMap = mutableMapOf()
        secondShiftTimetableMap[DayOfWeek.MONDAY] = mondayPair.second!!
        secondShiftTimetableMap[DayOfWeek.TUESDAY] = tuesdayPair.second!!
        secondShiftTimetableMap[DayOfWeek.WEDNESDAY] = wednesdayPair.second!!
        secondShiftTimetableMap[DayOfWeek.THURSDAY] = thursdayPair.second!!
        secondShiftTimetableMap[DayOfWeek.FRIDAY] = fridayPair.second!!
        secondShiftTimetableMap[DayOfWeek.SATURDAY] = saturdayPair.second!!
    }
    return Pair(firstShiftTimetableMap, secondShiftTimetableMap)
}

fun getTeacher(teacherID: Int): Pair<Teacher, Int?> {
    require(doesUserExist(teacherID)) { "User with ID $teacherID does not exist" }
    val cachedTeacher = findCachedTeacher(teacherID)
    if (cachedTeacher!=null)
        return cachedTeacher
    val teacherData = transaction {
        Teachers.select {
            Teachers.id eq teacherID
        }.firstOrNull()
    } ?: throw IllegalArgumentException("Teacher with ID $teacherID does not exist")
    val teacher = Teacher(
        teacherID,
        teacherData[Teachers.firstName],
        teacherData[Teachers.middleName],
        teacherData[Teachers.lastName]
    )
    val classID = teacherData[Teachers.classID]
    cacheTeacher(teacher, classID)
    return Pair(teacher, classID)
}