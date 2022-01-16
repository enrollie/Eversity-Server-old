/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.DATABASE
import by.enrollie.eversity.data_classes.*
import by.enrollie.eversity.database.classesAbsenceCache
import by.enrollie.eversity.database.xodus_definitions.*
import by.enrollie.eversity.database.xodus_definitions.XodusAbsenceReason.Companion.DUMMY
import by.enrollie.eversity.database.xodus_definitions.XodusAbsenceReason.Companion.toAbsenceReason
import by.enrollie.eversity_plugins.plugin_api.AbsenceData
import by.enrollie.eversity_plugins.plugin_api.AbsenceEngine
import by.enrollie.eversity_plugins.plugin_api.AbsenceEngineEventListener
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.dnq.creator.findOrNew
import kotlinx.dnq.query.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.joda.time.DateTime
import java.util.*

private val coroutineScope = CoroutineScope(Dispatchers.Main)

private val eventListeners = mutableListOf<AbsenceEngineEventListener>()

private fun MutableList<AbsenceEngineEventListener>.runOnEachAsync(lambda: (AbsenceEngineEventListener) -> Unit) {
    coroutineScope.launch {
        this@runOnEachAsync.forEach(lambda)
    }
}

val absenceEngineImpl = object : AbsenceEngine {
    override fun registerEventListener(listener: AbsenceEngineEventListener): String {
        eventListeners.add(listener)
        return UUID.nameUUIDFromBytes(listener.hashCode().toString().toByteArray()).toString()
    }

    override fun deregisterEventListener(id: String) {
        eventListeners.removeIf { UUID.nameUUIDFromBytes(it.hashCode().toString().toByteArray()).toString() == id }
    }
}

/**
 *
 */
fun insertAbsences(absencesList: List<Absence>, store: TransientEntityStore = DATABASE) =
    store.transactional {
        for (absence in absencesList) {
            XodusAbsence.findOrNew(
                XodusAbsence.query(
                    (XodusAbsence::date eq absence.date.withTimeAtStartOfDay()) and (XodusAbsence::pupil.matches(
                        XodusPupilProfile::user.matches(XodusUser::id eq absence.pupilID)
                    ))
                )
            ) {
                pupil =
                    XodusPupilProfile.query(XodusPupilProfile::user.matches(XodusUser::id eq absence.pupilID)).first()
                date = absence.date.withTimeAtStartOfDay()
                lessons = absence.lessonsList.sorted().toSet()
                schoolClass = XodusClass.query(XodusClass::id eq absence.classID).first()
                sentBy = XodusUser.query(XodusUser::id eq (absence.sentByID ?: Int.MIN_VALUE)).firstOrNull()
                additionalNotes = absence.additionalNote?.let { note ->
                    XodusAbsenceNotes.new {
                        noteType = when (note) {
                            is TextAbsenceNote -> XodusAbsenceNoteType.TEXT
                            is DataAbsenceNote -> XodusAbsenceNoteType.ADDITIONAL_DATA
                            else -> {
                                error("Unknown absence note type: ${note::class.qualifiedName}")
                            }
                        }
                        this.note = AbsenceNoteJSON.encodeToString(note)
                    }
                }
                reason = absence.reason.toXodusReason()
            }.apply {
                if (absence.lessonsList.isEmpty()) {
                    eventListeners.runOnEachAsync { eventListener ->
                        eventListener.onRemove(
                            AbsenceData(
                                this.pupil?.user?.id,
                                absence.classID,
                                by.enrollie.eversity_plugins.plugin_api.AbsenceReason.valueOf(this.reason.toAbsenceReason().name),
                                absence.date.withTimeAtStartOfDay(),
                                absence.sentByID,
                                absence.lessonsList
                            )
                        )
                    }
                    delete()
                }
                if (XodusAbsence.query((XodusAbsence::schoolClass.matches(XodusClass::id eq absence.classID) and (XodusAbsence::date eq absence.date.withTimeAtStartOfDay()))).isEmpty) {
                    eventListeners.runOnEachAsync { eventListener ->
                        eventListener.onAdd(
                            AbsenceData(
                                null,
                                absence.classID,
                                by.enrollie.eversity_plugins.plugin_api.AbsenceReason.DUMMY,
                                absence.date.withTimeAtStartOfDay(),
                                absence.sentByID,
                                absence.lessonsList
                            )
                        )
                    }
                    insertEmptyAbsence(absence.classID, absence.date.withTimeAtStartOfDay())
                }
            }
            classesAbsenceCache.invalidate(Pair(absence.classID, absence.date.withTimeAtStartOfDay()))
        }
    }

private fun insertEmptyAbsence(classID: Int, date: DateTime) {
    XodusAbsence.query((XodusAbsence::schoolClass.matches(XodusClass::id eq classID)) and (XodusAbsence::date eq date.withTimeAtStartOfDay()))
        .toList().forEach { it.delete() }
    XodusAbsence.new {
        schoolClass = XodusClass.query(XodusClass::id eq classID).first()
        this.date = date.withTimeAtStartOfDay()
        pupil = null
        reason = DUMMY
    }
    eventListeners.runOnEachAsync {
        it.onAdd(
            AbsenceData(
                null,
                classID,
                by.enrollie.eversity_plugins.plugin_api.AbsenceReason.DUMMY,
                date.withTimeAtStartOfDay(),
                null,
                listOf()
            )
        )
    }
}

/**
 * Removes absence from database
 * @param pupil Pupil to remove absence for
 * @param date to remove absence
 */
fun removeAbsence(pupil: Pupil, date: DateTime, store: TransientEntityStore = DATABASE) = store.transactional {
    classesAbsenceCache.invalidate(Pair(pupil.classID, date.withTimeAtStartOfDay()))
    XodusAbsence.query((XodusAbsence::pupil.matches(XodusPupilProfile::user.matches(XodusUser::id eq pupil.id))) and (XodusAbsence::date eq date))
        .firstOrNull()?.apply {
            eventListeners.runOnEachAsync {
                it.onRemove(
                    AbsenceData(
                        this.pupil?.user?.id,
                        schoolClass.id,
                        by.enrollie.eversity_plugins.plugin_api.AbsenceReason.valueOf(reason.toAbsenceReason().name),
                        this.date.withTimeAtStartOfDay(),
                        this.sentBy?.id, this.lessons.toList()
                    )
                )
            }
            delete()
        }
    if (XodusAbsence.query((XodusAbsence::schoolClass.matches(XodusClass::id eq pupil.classID)) and (XodusAbsence::date eq date)).isEmpty)
        insertEmptyAbsence(pupil.classID, date)
}

/**
 * Inserts dummy absence to make class look like it sent an absence data
 * @param classID ID of class
 * @param date Dat
 */
fun insertDummyAbsence(classID: Int, date: DateTime, store: TransientEntityStore = DATABASE) {
    classesAbsenceCache.invalidate(Pair(classID, date.withTimeAtStartOfDay()))
    store.transactional { insertEmptyAbsence(classID, date) }
}

/**
 * Gets absence data for given date constraints.
 * @param dateRange Pair of (search begin date, search end date)
 */
fun getClassAbsence(
    classID: Int,
    dateRange: Pair<DateTime, DateTime>,
    store: TransientEntityStore = DATABASE
): Set<Absence> =
    store.transactional(readonly = true) {
        XodusAbsence.query((XodusAbsence::schoolClass.matches(XodusClass::id eq classID)) and (XodusAbsence::date ge dateRange.first.withTimeAtStartOfDay()) and (XodusAbsence::date le dateRange.second.withTimeAtStartOfDay()) and (XodusAbsence::pupil ne null))
            .toList().map {
                Absence(
                    it.pupil!!.user.id,
                    it.schoolClass.id,
                    it.sentBy?.id,
                    it.date,
                    it.reason.toAbsenceReason(),
                    it.lessons.toList(),
                    it.additionalNotes?.let {
                        AbsenceNoteJSON.decodeFromString(it.note)
                    })
            }.toSet()
    }

/**
 * Gets class absence for given day
 */
fun getClassAbsence(classID: Int, day: DateTime, store: TransientEntityStore = DATABASE): Set<Absence> =
    classesAbsenceCache.get(
        Pair(classID, day.withTimeAtStartOfDay())
    ) {
        store.transactional(readonly = true) {
            XodusAbsence.query((XodusAbsence::schoolClass.matches(XodusClass::id eq classID)) and (XodusAbsence::date le day.withTimeAtStartOfDay()) and (XodusAbsence::pupil ne null))
                .toList().map {
                    Absence(
                        it.pupil!!.user.id,
                        it.schoolClass.id,
                        it.sentBy?.id,
                        it.date,
                        it.reason.toAbsenceReason(),
                        it.lessons.toList(),
                        it.additionalNotes?.let {
                            AbsenceNoteJSON.decodeFromString(it.note)
                        })
                }.toSet()
        }
    }

/**
 * Retrieves absence statistics from database for [date]
 * @return Pair of absence statistics for first shift and second shift respectively
 */
fun getAbsenceStatistics(
    date: DateTime,
    store: TransientEntityStore = DATABASE
): Pair<Map<AbsenceReason, Int>, Map<AbsenceReason, Int>> = store.transactional(readonly = true) {
    val firstShiftAbsences =
        XodusAbsence.query((XodusAbsence::schoolClass.matches(XodusClass::isSecondShift eq false)) and (XodusAbsence::date eq date.withTimeAtStartOfDay()) and (XodusAbsence::pupil ne null))
            .toList().map {
                Absence(
                    it.pupil!!.user.id,
                    it.schoolClass.id,
                    it.sentBy?.id,
                    it.date,
                    it.reason.toAbsenceReason(),
                    it.lessons.toList(),
                    it.additionalNotes?.let {
                        AbsenceNoteJSON.decodeFromString(it.note)
                    })
            }
    val secondShiftAbsences =
        XodusAbsence.query((XodusAbsence::schoolClass.matches(XodusClass::isSecondShift eq true)) and (XodusAbsence::date eq date.withTimeAtStartOfDay()) and (XodusAbsence::pupil ne null))
            .toList().map {
                Absence(
                    it.pupil!!.user.id,
                    it.schoolClass.id,
                    it.sentBy?.id,
                    it.date,
                    it.reason.toAbsenceReason(),
                    it.lessons.toList(),
                    it.additionalNotes?.let {
                        AbsenceNoteJSON.decodeFromString(it.note)
                    })
            }
    Pair(
        AbsenceReason.values().filter { it != AbsenceReason.DUMMY }
            .associateWith { reason -> firstShiftAbsences.count { it.reason == reason } },
        AbsenceReason.values().filter { it != AbsenceReason.DUMMY }
            .associateWith { reason -> secondShiftAbsences.count { it.reason == reason } })
}

/**
 * Returnes list of classes that did not send any absence data at that date
 */
fun getNoAbsenceDataClasses(date: DateTime, store: TransientEntityStore = DATABASE): List<SchoolClass> =
    store.transactional(readonly = true) {
        XodusClass.query(
            not(
                XodusClass::id inValues XodusAbsence.query(XodusAbsence::date eq date.withTimeAtStartOfDay()).toList()
                    .map { it.schoolClass.id })
        ).toList().map {
            SchoolClass(
                it.id,
                it.classTitle,
                it.isSecondShift,
                it.classTeacher.user.id,
                it.pupils.toList().toPupilsArray()
            )
        }
    }
