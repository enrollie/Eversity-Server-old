/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.data_classes.Mark
import by.enrollie.eversity.database.tables.Marks
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.text.SimpleDateFormat
import java.util.*

fun insertMark(mark: Mark, journalID: Int, lessonID: Int) {
    require(doesUserExist(mark.pupil.id)) { "Pupil with ID ${mark.pupil.id} does not exist" }
    require(doesClassExist(mark.pupil.classID)) { "Class with ID ${mark.pupil.classID} does not exist" }
    transaction {
        Marks.deleteWhere {
            Marks.id eq mark.id
        }
        Marks.insert {
            it[Marks.id] = mark.id
            it[Marks.value] = mark.markNum
            it[Marks.pupilID] = mark.pupil.id
            it[Marks.classID] = mark.pupil.classID
            it[Marks.date] = SimpleDateFormat("YYYY-MM-dd").format(Calendar.getInstance().time)
            it[Marks.journalID] = journalID
            it[Marks.lessonID] = lessonID
        }
    }
}

fun batchInsertMarks(marksList: List<Pair<Mark, Pair<Int, Int>>>) {
    marksList.forEach {
        require(doesUserExist(it.first.pupil.id)) { "Pupil with ID ${it.first.pupil.id} does not exist" }
        require(doesClassExist(it.first.pupil.classID)) { "Class with ID ${it.first.pupil.classID} does not exist" }
    }
    transaction {
        for (mark in marksList) {
            Marks.deleteWhere {
                Marks.id eq mark.first.id
            }
        }
        Marks.batchInsert(marksList, ignore = true, shouldReturnGeneratedValues = false) {
            val mark = it.first
            this[Marks.id] = mark.id
            this[Marks.value] = mark.markNum
            this[Marks.pupilID] = mark.pupil.id
            this[Marks.classID] = mark.pupil.classID
            this[Marks.date] = SimpleDateFormat("YYYY-MM-dd").format(Calendar.getInstance().time)
            this[Marks.journalID] = it.second.first
            this[Marks.lessonID] = it.second.second
        }
    }
}

fun getMarkID(pupilID: Int, journalID: Int, lessonID: Int):Pair<Int,Short?>?{
    val r = transaction {
        Marks.select {
            (Marks.journalID eq journalID) and
            (Marks.lessonID eq lessonID) and
            (Marks.pupilID eq pupilID)
        }.toList().firstOrNull()
    } ?: return null
    return Pair(r[Marks.id], r[Marks.value])
}