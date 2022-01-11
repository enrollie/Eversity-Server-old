/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_functions

import by.enrollie.eversity.SCHOOL_NAME
import by.enrollie.eversity.data_classes.AbsenceReason
import org.docx4j.model.datastorage.migration.VariablePrepare
import org.docx4j.openpackaging.packages.WordprocessingMLPackage
import org.joda.time.DateTime
import java.io.File
import java.io.InputStream

/**
 * Fills absence statistics [template] with [absenceData]. See https://github.com/enrollie/eversity-server for template example.
 * @param pupilCount Pair of first shift pupil count and second shift pupil count respectively
 * @param date Date of template filling (defaults to function call date)
 * @throws org.docx4j.openpackaging.exceptions.Docx4JException Thrown, if [template] does not contain valid docx file.
 */
fun fillAbsenceTemplate(
    absenceData: Pair<Map<AbsenceReason, Int>, Map<AbsenceReason, Int>>,
    pupilCount: Pair<Int, Int>,
    template: InputStream,
    date: String = DateTime.now().toString("dd.MM.YYYY")
): File {
    val wordProcess =
        WordprocessingMLPackage.load(template)
    VariablePrepare.prepare(wordProcess)
    val mainDocumentPart = wordProcess.mainDocumentPart
    val mappings = mutableMapOf<String, String>()
    mappings["current_date"] = date
    mappings["school_name"] = SCHOOL_NAME.nominative
    run {
        mappings["first_shift_total_count"] = pupilCount.first.toString()
        val attended: Int
        var counter = 0
        absenceData.first.forEach { counter += it.value }
        attended = pupilCount.first - counter
        mappings["first_shift_attended"] = attended.toString()
        mappings["first_shift_attended_percent"] =
            "${
                String.format(
                    "%.1f",
                    ((attended.toDouble() / pupilCount.first.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0)
            }%"
        mappings["first_shift_ill_total"] = absenceData.first[AbsenceReason.ILLNESS].toString()
        mappings["first_shift_ill_percent"] = "${
            String.format(
                "%.1f",
                (((absenceData.first[AbsenceReason.ILLNESS] ?: 0).toDouble() / pupilCount.first.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0
            )
        }%"
        mappings["first_shift_sanatorium_total"] = absenceData.first[AbsenceReason.HEALING].toString()
        mappings["first_shift_sanatorium_percent"] = "${
            String.format(
                "%.1f",
                (((absenceData.first[AbsenceReason.HEALING] ?: 0).toDouble() / pupilCount.first.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0
            )
        }%"
        mappings["first_shift_request_total"] = absenceData.first[AbsenceReason.REQUEST].toString()
        mappings["first_shift_request_percent"] = "${
            String.format(
                "%.1f",
                (((absenceData.first[AbsenceReason.REQUEST] ?: 0).toDouble() / pupilCount.first.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0
            )
        }%"
        mappings["first_shift_competition_total"] = absenceData.first[AbsenceReason.COMPETITION].toString()
        mappings["first_shift_competition_percent"] = "${
            String.format(
                "%.1f",
                (((absenceData.first[AbsenceReason.COMPETITION] ?: 0).toDouble() / pupilCount.first.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0
            )
        }%"
        mappings["first_shift_unknown_total"] = absenceData.first[AbsenceReason.UNKNOWN].toString()
        mappings["first_shift_unknown_percent"] = "${
            String.format(
                "%.1f",
                (((absenceData.first[AbsenceReason.UNKNOWN] ?: 0).toDouble() / pupilCount.first.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0
            )
        }%"
    }
    run {
        mappings["second_shift_total_count"] = pupilCount.second.toString()
        val attended: Int
        var counter = 0
        absenceData.second.forEach { counter += it.value }
        attended = pupilCount.second - counter
        mappings["second_shift_attended"] = attended.toString()
        mappings["second_shift_attended_percent"] =
            "${
                String.format(
                    "%.1f",
                    ((attended.toDouble() / pupilCount.second.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0)
            }%"
        mappings["second_shift_ill_total"] = absenceData.second[AbsenceReason.ILLNESS].toString()
        mappings["second_shift_ill_percent"] = "${
            String.format(
                "%.1f",
                (((absenceData.second[AbsenceReason.ILLNESS] ?: 0).toDouble() / pupilCount.second.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0
            )
        }%"
        mappings["second_shift_sanatorium_total"] = absenceData.second[AbsenceReason.HEALING].toString()
        mappings["second_shift_sanatorium_percent"] = "${
            String.format(
                "%.1f",
                (((absenceData.second[AbsenceReason.HEALING] ?: 0).toDouble() / pupilCount.second.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0
            )
        }%"
        mappings["second_shift_request_total"] = absenceData.second[AbsenceReason.REQUEST].toString()
        mappings["second_shift_request_percent"] = "${
            String.format(
                "%.1f",
                (((absenceData.second[AbsenceReason.REQUEST] ?: 0).toDouble() / pupilCount.second.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0
            )
        }%"
        mappings["second_shift_competition_total"] =
            absenceData.second[AbsenceReason.COMPETITION].toString()
        mappings["second_shift_competition_percent"] = "${
            String.format(
                "%.1f",
                (((absenceData.second[AbsenceReason.COMPETITION] ?: 0).toDouble() / pupilCount.second.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0
            )
        }%"
        mappings["second_shift_unknown_total"] = absenceData.second[AbsenceReason.UNKNOWN].toString()
        mappings["second_shift_unknown_percent"] = "${
            String.format(
                "%.1f",
                (((absenceData.second[AbsenceReason.UNKNOWN] ?: 0).toDouble() / pupilCount.second.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0
            )
        }%"
    }
    val tempFile = File.createTempFile("eversity_template-", ".docx")
    mainDocumentPart.variableReplace(mappings)
    wordProcess.save(tempFile)
    return tempFile
}
