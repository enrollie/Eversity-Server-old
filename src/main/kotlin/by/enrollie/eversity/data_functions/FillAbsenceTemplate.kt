/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_functions

import by.enrollie.eversity.SCHOOL_NAME
import by.enrollie.eversity.data_classes.AbsenceReason
import fr.opensagres.xdocreport.document.registry.XDocReportRegistry
import fr.opensagres.xdocreport.template.TemplateEngineKind
import org.joda.time.DateTime
import java.io.File
import java.io.InputStream

/**
 * Fills absence statistics [templateInputStream] with [absenceData]. See https://github.com/enrollie/eversity-server for template example.
 * @param pupilCount Pair of first shift pupil count and second shift pupil count respectively
 * @param date Date of template filling (defaults to function call date)
 * @throws org.docx4j.openpackaging.exceptions.Docx4JException Thrown, if [templateInputStream] does not contain valid docx file.
 */
fun fillAbsenceTemplate(
    absenceData: Pair<Map<AbsenceReason, Int>, Map<AbsenceReason, Int>>,
    pupilCount: Pair<Int, Int>,
    date: DateTime,
    templateInputStream: InputStream,
    outputFile: File,
) {
    @Suppress("UNUSED") // Used in DocX
    data class AbsenceReport(
        val totalPupils: Int,
        val totalIll: Int,
        val totalHealing: Int,
        val totalRequest: Int,
        val totalCompetition: Int,
        val totalUnknown: Int,
    ) {
        val totalAttended: Int
            get() = totalPupils - (totalIll + totalHealing + totalRequest + totalCompetition + totalUnknown)
        val totalAttendedPercent: String
            get() = "${
                String.format(
                    "%.1f",
                    ((totalAttended.toDouble() / totalPupils.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0)
            }%"
        val totalIllPercent: String
            get() = "${
                String.format(
                    "%.1f",
                    ((totalIll.toDouble() / totalPupils.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0)
            }%"
        val totalHealingPercent: String
            get() = "${
                String.format(
                    "%.1f",
                    ((totalHealing.toDouble() / totalPupils.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0)
            }%"
        val totalRequestPercent: String
            get() = "${
                String.format(
                    "%.1f",
                    ((totalRequest.toDouble() / totalPupils.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0)
            }%"
        val totalCompetitionPercent: String
            get() = "${
                String.format(
                    "%.1f",
                    ((totalCompetition.toDouble() / totalPupils.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0)
            }%"
        val totalUnknownPercent: String
            get() = "${
                String.format(
                    "%.1f",
                    ((totalUnknown.toDouble() / totalPupils.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0)
            }%"
    }

    val firstShiftData = absenceData.first.let {
        AbsenceReport(pupilCount.first,
            it[AbsenceReason.ILLNESS]!!,
            it[AbsenceReason.HEALING]!!,
            it[AbsenceReason.REQUEST]!!,
            it[AbsenceReason.COMPETITION]!!,
            it[AbsenceReason.UNKNOWN]!!)
    }
    val secondShiftData = absenceData.second.let {
        AbsenceReport(pupilCount.first,
            it[AbsenceReason.ILLNESS]!!,
            it[AbsenceReason.HEALING]!!,
            it[AbsenceReason.REQUEST]!!,
            it[AbsenceReason.COMPETITION]!!,
            it[AbsenceReason.UNKNOWN]!!)
    }
    val report = XDocReportRegistry.getRegistry().loadReport(templateInputStream, TemplateEngineKind.Velocity)
    val fieldsMetadata = report.createFieldsMetadata().apply {
        load("firstShift", AbsenceReport::class.java)
        load("secondShift", AbsenceReport::class.java)
        addFieldReplacement("current_date", date.toString("dd.MM.YYYY"))
        addFieldReplacement("school_name", SCHOOL_NAME.nominative)
    }
    report.fieldsMetadata = fieldsMetadata
    val context = report.createContext()
    context.put("firstShift", firstShiftData)
    context.put("secondShift", secondShiftData)
    outputFile.createNewFile()
    report.process(context, outputFile.outputStream())
}
