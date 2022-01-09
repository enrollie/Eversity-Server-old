/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.DATABASE
import by.enrollie.eversity.data_classes.*
import by.enrollie.eversity.database.xodus_definitions.XodusAbsence
import by.enrollie.eversity.database.xodus_definitions.XodusAbsenceReason.Companion.toAbsenceReason
import by.enrollie.eversity.database.xodus_definitions.XodusClass
import by.enrollie.eversity.database.xodus_definitions.XodusPupilProfile
import by.enrollie.eversity.database.xodus_definitions.toPupilsArray
import by.enrollie.eversity.exceptions.noClassError
import kotlinx.dnq.query.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants

/**
 * Checks, whether class exists.
 * @param classID ID of class
 *
 * @return True, if class exists. False otherwise.
 */
fun doesClassExist(classID: Int): Boolean =
    DATABASE.transactional(readonly = true) { XodusClass.query(XodusClass::id eq classID).any() }

/**
 * Returns class timetable
 * @param classID ID of class
 * @throws NoSuchElementException Thrown, if class was not found
 * @return Map, containing six arrays of lessons (may be unsorted), mapped to six [DayOfWeek]'s (from Monday to Saturday)
 */
fun getClassTimetable(classID: Int): Timetable {
    val timetableQuery = DATABASE.transactional(readonly = true) {
        XodusClass.query(XodusClass::id eq classID).first().timetable
    }
    val timetableMap = mutableMapOf<DayOfWeek, Array<Lesson>>()
    timetableMap[DayOfWeek.MONDAY] =
        Json.decodeFromString(timetableQuery.monday)
    timetableMap[DayOfWeek.TUESDAY] =
        Json.decodeFromString(timetableQuery.tuesday)
    timetableMap[DayOfWeek.WEDNESDAY] =
        Json.decodeFromString(timetableQuery.wednesday)
    timetableMap[DayOfWeek.THURSDAY] =
        Json.decodeFromString(timetableQuery.thursday)
    timetableMap[DayOfWeek.FRIDAY] =
        Json.decodeFromString(timetableQuery.friday)
    timetableMap[DayOfWeek.SATURDAY] =
        Json.decodeFromString(timetableQuery.saturday)
    return Timetable(timetableMap)
}

/**
 * Returns [SchoolClass] based on given [classID]
 * @throws NoSuchElementException Thrown if class was not found
 */
fun getClass(classID: Int): SchoolClass = DATABASE.transactional(readonly = true) {
    XodusClass.query(XodusClass::id eq classID).firstOrNull()?.let {
        SchoolClass(it.id, it.classTitle, it.isSecondShift, it.classTeacher.user.id, it.pupils.toList().map {
            Pupil(it.user.id, it.user.firstName, it.user.middleName, it.user.lastName, classID)
        }.toTypedArray())
    } ?: throw NoSuchElementException("No class with ID $classID")
}

fun countPupils(): Pair<Int, Int> = DATABASE.transactional(readonly = true) {
    val pupilsMap = XodusPupilProfile.all().toList().groupBy {
        it.schoolClass.isSecondShift
    }
    return@transactional Pair((pupilsMap[false]?.size ?: 0), (pupilsMap[true]?.size ?: 0))
}

fun getClassStatistics(
    classID: Int,
    startDate: DateTime = DateTime.now().withDayOfWeek(DateTimeConstants.MONDAY),
    endDate: DateTime = DateTime.now().withDayOfWeek(DateTimeConstants.SATURDAY)
): List<Pair<Pupil, ExtendedPupilAbsenceStatistics>> {
    val (pupils, absences) = DATABASE.transactional(readonly = true) {
        Pair(
            XodusClass.query(XodusClass::id eq classID).firstOrNull()?.pupils?.toList()?.toPupilsArray()
                ?: noClassError(classID),
            XodusAbsence.query(
                (XodusAbsence::date ge startDate) and (XodusAbsence::date le endDate) and (XodusAbsence::schoolClass.matches(
                    XodusClass::id eq classID
                ) and (XodusAbsence::pupil ne null))
            ).toList().map {
                Absence(
                    it.pupil!!.user.id,
                    it.schoolClass.id,
                    it.sentBy?.id,
                    it.date,
                    it.reason.toAbsenceReason(),
                    it.lessons.toList(),
                    it.additionalNotes?.let { note->
                        AbsenceNoteJSON.decodeFromString(note.note)
                    }
                )
            }
        )
    }
    val resultList = mutableListOf<Pair<Pupil, ExtendedPupilAbsenceStatistics>>()
    pupils.forEach { pupil ->
        resultList.add(
            Pair(pupil, ExtendedPupilAbsenceStatistics(absences.filter { it.pupilID == pupil.id }))
        )
    }
    return resultList
}
