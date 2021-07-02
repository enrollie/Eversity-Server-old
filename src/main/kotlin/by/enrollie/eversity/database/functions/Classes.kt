/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.data_classes.DayOfWeek
import by.enrollie.eversity.data_classes.Lesson
import by.enrollie.eversity.data_classes.Pupil
import by.enrollie.eversity.data_classes.SchoolClass
import by.enrollie.eversity.database.cacheClass
import by.enrollie.eversity.database.cacheClassTimetable
import by.enrollie.eversity.database.findCachedClass
import by.enrollie.eversity.database.findCachedClassTimetable
import by.enrollie.eversity.database.tables.ClassTimetables
import by.enrollie.eversity.database.tables.Classes
import by.enrollie.eversity.database.tables.Pupils
import by.enrollie.eversity.exceptions.ClassNotRegistered
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Checks, whether class exists.
 * @param classID ID of class
 *
 * @return True, if class exists. False otherwise.
 */
fun doesClassExist(classID: Int): Boolean {
    return transaction {
        Classes.select { Classes.classID eq classID }.toList()
    }.isNotEmpty()
}

/**
 * Determines, whether class should be updated or not
 * @param classID ID of class
 * @return True, if you SHOULD (!) update class data. False otherwise
 */
fun shouldUpdateClass(classID: Int): Boolean {
    val classExisting = doesClassExist(classID)
    val (existingPupils, existingTimetable) = transaction {
        Pair(
            Pupils.select {
                Pupils.classID eq classID
            }.toList().isNotEmpty(),
            ClassTimetables.select {
                ClassTimetables.id eq classID
            }.toList().isNotEmpty()
        )
    }
    if (classExisting) {
        if (existingPupils) {
            if (existingTimetable)
                return false
        }
    }
    return true
}

/**
 * Returns class timetable
 * @param classID ID of class
 * @return Map, containing six arrays of lessons (may be unsorted), mapped to six [DayOfWeek]'s (from Monday to Saturday)
 */
fun getClassTimetable(classID: Int): Map<DayOfWeek, Array<Lesson>> {
    if (!doesClassExist(classID)) {
        throw ClassNotRegistered("Class with ID $classID does not exist")
    }
    val cachedTimetable = findCachedClassTimetable(classID)
    if (cachedTimetable != null)
        return cachedTimetable
    val timetableQuery = transaction {
        ClassTimetables.select {
            ClassTimetables.id eq classID
        }.toList().firstOrNull()
    }
        ?: throw NoSuchElementException("Class timetable of class with id $classID was not found. Database is waiting for bad times")
    val timetableMap = mutableMapOf<DayOfWeek, Array<Lesson>>()
    //Danger: big chunk of code incoming
    timetableMap[DayOfWeek.MONDAY] =
        Json.decodeFromString(timetableQuery[ClassTimetables.monday])
    timetableMap[DayOfWeek.TUESDAY] =
        Json.decodeFromString(timetableQuery[ClassTimetables.tuesday])
    timetableMap[DayOfWeek.WEDNESDAY] =
        Json.decodeFromString(timetableQuery[ClassTimetables.wednesday])
    timetableMap[DayOfWeek.THURSDAY] =
        Json.decodeFromString(timetableQuery[ClassTimetables.thursday])
    timetableMap[DayOfWeek.FRIDAY] =
        Json.decodeFromString(timetableQuery[ClassTimetables.friday])
    timetableMap[DayOfWeek.SATURDAY] =
        Json.decodeFromString(timetableQuery[ClassTimetables.saturday])
    cacheClassTimetable(classID, timetableMap)
    return timetableMap
}

fun getClass(classID: Int): SchoolClass {
    require(doesClassExist(classID)) { "Class with ID $classID does not exist." }
    val cachedClass = findCachedClass(classID)
    if (cachedClass != null)
        return cachedClass
    val (classData, pupilsData) = transaction {
        return@transaction Pair(
            Classes.select {
                Classes.classID eq classID
            }.toList().firstOrNull(),
            Pupils.select {
                Pupils.classID eq classID
            }.toList()
        )
    }
    if (classData == null)
        throw IllegalStateException("Class data is null")
    val pupilsList = mutableListOf<Pupil>()
    pupilsData.forEach {
        pupilsList.add(
            Pupil(
                it[Pupils.id],
                it[Pupils.firstName],
                it[Pupils.lastName],
                it[Pupils.classID]
            )
        )
    }
    val result = SchoolClass(
        classData[Classes.classID],
        classData[Classes.name],
        classData[Classes.classTeacher],
        pupilsList.toTypedArray()
    )
    cacheClass(result)
    return result
}

fun countPupils(): Pair<Int, Int> {
    val (firstShift, secondShift) = transaction {
        val shifts = Classes.selectAll().toList().map { Pair(it[Classes.classID], it[Classes.isSecondShift]) }
        val firstShiftClasses = shifts.filter { !it.second }.map { it.first }
        val secondShiftClasses = shifts.filter { it.second }.map { it.first }
        val pupils = Pupils.selectAll().toList().map { Pair(it[Pupils.id], it[Pupils.classID]) }
        return@transaction Pair(
            pupils.filter { firstShiftClasses.contains(it.second) }.size,
            pupils.filter { secondShiftClasses.contains(it.second) }.size
        )
    }
    return Pair(firstShift, secondShift)
}