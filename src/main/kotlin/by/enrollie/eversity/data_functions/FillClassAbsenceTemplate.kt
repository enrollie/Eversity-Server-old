/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_functions

import by.enrollie.eversity.data_classes.ExtendedPupilAbsenceStatistics
import by.enrollie.eversity.data_classes.Pupil
import by.enrollie.eversity.database.functions.getClass
import fr.opensagres.xdocreport.document.registry.XDocReportRegistry
import fr.opensagres.xdocreport.template.TemplateEngineKind
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

fun fillClassAbsenceTemplate(
    templateInputStream: InputStream,
    absencePackage: List<Pair<Pupil, ExtendedPupilAbsenceStatistics>>,
    classID: Int,
    dates: Pair<String, String>
): File {
    data class FillInfo(
        val classTitle: String,
        val beginDate: String,
        val endDate: String,
        val fillDate: String
    )

    data class Absence(
        val pupilPlace: Int,
        val pupilName: String,
        val totalSkippedLessons: Int,
        val illnessLessons: Int,
        val requestLessons: Int,
        val healingLessons: Int,
        val competitionLessons: Int,
        val unknownDays: Int,
        val unknownLessons: Int
    )

    data class AbsenceSummary(
        val summary: Int,
        val illness: Int,
        val request: Int,
        val healing: Int,
        val competition: Int,
        val unknownDays: Int,
        val unknownLessons: Int
    )

    val absences = absencePackage.mapIndexed { index, data ->
        Absence(
            index + 1,
            "${data.first.lastName} ${data.first.firstName}",
            data.second.sumLessons,
            data.second.illnessLessons,
            data.second.requestLessons,
            data.second.healingLessons,
            data.second.competitionLessons,
            data.second.unknownDays,
            data.second.unknownLessons
        )
    }
    val fillInfo = FillInfo(
        getClass(classID).title,
        dates.first,
        dates.second,
        SimpleDateFormat("dd.mm.YYYY").format(Calendar.getInstance().time)
    )
    val summaries = AbsenceSummary(
        absences.sumOf { it.totalSkippedLessons },
        absences.sumOf { it.illnessLessons },
        absences.sumOf { it.requestLessons },
        absences.sumOf { it.healingLessons },
        absences.sumOf { it.competitionLessons },
        absences.sumOf { it.unknownDays },
        absences.sumOf { it.unknownLessons }
    )

    val report = XDocReportRegistry.getRegistry().loadReport(templateInputStream, TemplateEngineKind.Velocity)
    val fieldsMetadata = report.createFieldsMetadata().apply {
        load("absence", Absence::class.java)
        load("info", FillInfo::class.java)
        load("absenceSummary", AbsenceSummary::class.java)
        addFieldAsList("absence.pupilPlace")
        addFieldAsList("absence.pupilName")
        addFieldAsList("absence.totalSkippedLessons")
        addFieldAsList("absence.illnessLessons")
        addFieldAsList("absence.requestLessons")
        addFieldAsList("absence.healingLessons")
        addFieldAsList("absence.competitionLessons")
        addFieldAsList("absence.unknownDays")
        addFieldAsList("absence.unknownLessons")
    }
    report.fieldsMetadata = fieldsMetadata
    val context = report.createContext()
    context.put("absenceSummary", summaries)
    context.put("absence", absences)
    context.put("info", fillInfo)

    val tempFile = File.createTempFile("EV-Class-$classID-stat", ".docx")
    report.process(context, tempFile.outputStream())
    return tempFile
}