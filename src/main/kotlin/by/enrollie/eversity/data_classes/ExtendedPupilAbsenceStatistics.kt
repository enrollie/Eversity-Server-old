/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

data class ExtendedPupilAbsenceStatistics(
    val illnessLessons: Int,
    val healingLessons: Int,
    val requestLessons: Int,
    val competitionLessons: Int,
    val unknownLessons: Int,
    val unknownDays: Int
) {
    constructor(absenceList: List<Absence>) : this(
        absenceList.count
        { it.reason == AbsenceReason.ILLNESS },
        absenceList.count
        { it.reason == AbsenceReason.HEALING },
        absenceList.count
        { it.reason == AbsenceReason.REQUEST },
        absenceList.count
        { it.reason == AbsenceReason.COMPETITION },
        absenceList.filter { it.reason == AbsenceReason.UNKNOWN }.sumOf { it.lessonsList.size },
        absenceList.count { it.reason == AbsenceReason.UNKNOWN && it.lessonsList.contains(1) })

    val sumLessons: Int
        get() = illnessLessons + healingLessons + requestLessons + competitionLessons + unknownLessons
}
