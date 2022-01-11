/*
 * Copyright © 2021 - 2022.
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
        absenceList.filter { it.reason == AbsenceReason.ILLNESS }.sumOf { it.lessonsList.size },
        absenceList.filter
        { it.reason == AbsenceReason.HEALING }.sumOf { it.lessonsList.size },
        absenceList.filter
        { it.reason == AbsenceReason.REQUEST }.sumOf { it.lessonsList.size },
        absenceList.filter
        { it.reason == AbsenceReason.COMPETITION }.sumOf { it.lessonsList.size },
        absenceList.filter { it.reason == AbsenceReason.UNKNOWN }.sumOf { it.lessonsList.size },
        absenceList.filter { it.reason == AbsenceReason.UNKNOWN && it.lessonsList.contains(1) }
            .sumOf { it.lessonsList.size })

    val sumLessons: Int
        get() = illnessLessons + healingLessons + requestLessons + competitionLessons + unknownLessons
}
