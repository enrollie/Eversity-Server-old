/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.data_classes.Absence
import by.enrollie.eversity.data_classes.AbsenceReason
import by.enrollie.eversity.data_classes.Pupil
import by.enrollie.eversity.database.tables.Absences
import by.enrollie.eversity.database.tables.Classes
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

fun insertAbsence(pupil: Pupil, reason: AbsenceReason, date: DateTime, absenceList: List<Pair<Short, Boolean>>) {
    if (pupil.id != -1)
        require(doesUserExist(pupil.id)) { "Pupil with ID ${pupil.id} does not exist" }
    require(doesClassExist(pupil.classID)) { "Class with ID ${pupil.classID} does not exist" }
    transaction {
        val existingAbsenceList = Absences.select {
            (Absences.pupilID eq pupil.id) and (Absences.date eq date)
        }.toList().firstOrNull()?.let {
            Json.decodeFromString<List<Short>>(it[Absences.absenceList])
        }
        val filteredAbsenceList = existingAbsenceList?.filter { lesson ->
            absenceList.find { it.first == lesson }?.let { !it.second } != true
        }?.plus(absenceList.filter { it.second }.map { it.first })?.toSet()
        Absences.deleteWhere {
            (Absences.pupilID eq pupil.id) and (Absences.date eq date)
        }
        if (pupil.id == -1)
            Absences.deleteWhere { (Absences.classID eq pupil.classID) and (Absences.date eq date) }
        else Absences.deleteWhere { (Absences.classID eq pupil.classID) and (Absences.pupilID eq null) and (Absences.date eq date) }
        Absences.insertIgnore { builder ->
            builder[pupilID] = if (pupil.id == -1) null else pupil.id
            builder[classID] = pupil.classID
            builder[Absences.date] = date
            builder[Absences.reason] = reason.name
            builder[Absences.absenceList] =
                Json.encodeToString(filteredAbsenceList ?: absenceList.filter { it.second }.map { it.first }.toSet())
        }
    }
}

/**
 * Removes absence from database
 * @param pupil Pupil to remove absence for
 * @param date to remove absence
 */
fun removeAbsence(pupil: Pupil, date: DateTime) {
    transaction {
        Absences.deleteWhere {
            (Absences.pupilID eq pupil.id) and
                    (Absences.date eq date)
        }
    }
}

/**
 * Gets absence data for given date constraints. Requires [startDate] to be before [endDate] to return right results
 */
fun getClassAbsence(classID: Int, startDate: String, endDate: String): Set<Absence> {
    require(doesClassExist(classID)) { "Class with ID $classID does not exist" }
    val startDateTime = DateTime.parse(startDate)
    val endDateTime = DateTime.parse(endDate)
    return transaction {
        Absences.select {
            (Absences.classID eq classID) and
                    (Absences.pupilID neq null) and
                    (Absences.date greaterEq startDateTime)
        }.toList().filter {
            it[Absences.date] <= endDateTime && it[Absences.pupilID] != null
        }.map {
            Absence(
                it[Absences.pupilID]!!,
                it[Absences.classID],
                it[Absences.date].toString("YYYY-MM-dd"),
                AbsenceReason.valueOf(it[Absences.reason]),
                Json.decodeFromString(it[Absences.absenceList])
            )
        }.toSet()
    }
}

/**
 * Gets class absence for given day
 */
fun getClassAbsence(classID: Int, day: String): Set<Absence> {
    require(doesClassExist(classID)) { "Class with ID $classID does not exist" }
    val requiredDateTime = DateTime.parse(day)
    return transaction {
        Absences.select {
            (Absences.classID eq classID) and
                    (Absences.date eq requiredDateTime) and
                    (Absences.pupilID neq null)
        }.toList().map {
            Absence(
                it[Absences.pupilID]!!,
                it[Absences.classID],
                it[Absences.date].toString("YYYY-MM-dd"),
                AbsenceReason.valueOf(it[Absences.reason]),
                Json.decodeFromString(it[Absences.absenceList])
            )
        }.toSet()
    }
}

/**
 * Gets class absence for [classID]
 */
fun getClassAbsence(classID: Int): Set<Absence> {
    require(doesClassExist(classID)) { "Class with ID $classID does not exist" }
    return transaction {
        Absences.select {
            (Absences.classID eq classID) and
                    (Absences.date eq DateTime.now()) and
                    (Absences.pupilID neq null)
        }.toList().map {
            Absence(
                it[Absences.pupilID]!!,
                it[Absences.classID],
                it[Absences.date].toString("YYYY-MM-dd"),
                AbsenceReason.valueOf(it[Absences.reason]),
                Json.decodeFromString(it[Absences.absenceList])
            )
        }.toSet()
    }
}

/**
 * Retrieves absence statistics from database for [date]
 * @return Pair of absence statistics for first shift and second shift respectively
 */
fun getAbsenceStatistics(date: DateTime = DateTime.now()): Pair<Map<AbsenceReason, Int>, Map<AbsenceReason, Int>> {
    val (absenceData, classesData) = transaction {
        Pair(
            Absences.select {
                Absences.date eq date
            }.toList().filter { it[Absences.pupilID] != null }.map {
                Absence(
                    it[Absences.pupilID]!!,
                    it[Absences.classID],
                    it[Absences.date].toString("YYYY-MM-dd"),
                    AbsenceReason.valueOf(it[Absences.reason]),
                    Json.decodeFromString(it[Absences.absenceList])
                )
            },
            Classes.selectAll().toList().map { Pair(it[Classes.classID], it[Classes.isSecondShift]) }
        )
    }
    var firstShiftAbsence: Map<AbsenceReason, Int> = mapOf()
    var secondShiftAbsence: Map<AbsenceReason, Int> = mapOf()
    repeat(2) { time ->
        val absenceMap = mutableMapOf(
            AbsenceReason.REQUEST to 0,
            AbsenceReason.UNKNOWN to 0,
            AbsenceReason.COMPETITION to 0,
            AbsenceReason.HEALING to 0,
            AbsenceReason.ILLNESS to 0
        )
        for (absence in absenceData.filter { absence ->
            classesData.filter { if (time == 0) !it.second else it.second }.find { it.first == absence.classID } != null
        }) {
            absenceMap[absence.reason] = (absenceMap[absence.reason] ?: 0) + 1
        }
        if (time == 0)
            firstShiftAbsence = absenceMap
        else secondShiftAbsence = absenceMap
    }
    return Pair(firstShiftAbsence, secondShiftAbsence)
}

fun getNoAbsenceDataClasses(date: DateTime = DateTime.now()): List<Triple<Int, String, Boolean>> {
    val (absenceData, classesList) = transaction {
        Pair(Absences.select {
            Absences.date eq date
        }.toList().map {
            Absence(
                it[Absences.pupilID] ?: -1,
                it[Absences.classID],
                it[Absences.date].toString("YYYY-MM-dd"),
                AbsenceReason.valueOf(it[Absences.reason]),
                Json.decodeFromString(it[Absences.absenceList])
            )
        }.toSet(),
            Classes.selectAll().toList()
                .map { Triple(it[Classes.classID], it[Classes.name], it[Classes.isSecondShift]) }
        )
    }
    val isClassPresent = mutableMapOf<Int, Boolean>()
    isClassPresent.putAll(classesList.map { Pair(it.first, false) })
    for (absence in absenceData) {
        isClassPresent[absence.classID] = true
    }
    val noDataClasses = isClassPresent.filter { !it.value }.map { it.key }
    return classesList.filter { noDataClasses.contains(it.first) }
}