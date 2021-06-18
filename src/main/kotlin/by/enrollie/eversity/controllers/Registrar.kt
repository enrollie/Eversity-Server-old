/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.controllers

import by.enrollie.eversity.database.EversityDatabase
import by.enrollie.eversity.schools_by.SchoolsWebWrapper
import io.ktor.util.*
import org.slf4j.Logger

class Registrar {
    companion object {
        lateinit var logger: Logger
            private set

        fun initialize(mainLogger: Logger) {
            synchronized(this) {
                logger = mainLogger
            }
        }
    }

    /**
     * Registers class (also fetches timetable and pupils)
     *
     * @param classID ID of class to register
     * @param schoolsWebWrapper Initialized [SchoolsWebWrapper] with valid cookies
     *
     * @return True, if class is registered, false, if not.
     *
     * @throws IllegalArgumentException Thrown, if [schoolsWebWrapper] doesn't have valid cookies.
     */
    suspend fun registerClass(classID: Int, className: String, schoolsWebWrapper: SchoolsWebWrapper): Boolean {
        if (!schoolsWebWrapper.validateCookies()) {
            val illegalArgumentException =
                IllegalArgumentException("Cookies are wrong (cookies validator returned false)")
            logger.error(illegalArgumentException)
            throw illegalArgumentException
        }
        if (!EversityDatabase.shouldUpdateClass(classID))
            return false
        val classTeacherID = schoolsWebWrapper.fetchClassForTeacher(classID)
        val classTimetable = schoolsWebWrapper.fetchClassTimetable(classID)
        val pupilsArray = schoolsWebWrapper.fetchPupilsArray(classID)
        var isSecondShift = false
        for (timetable in classTimetable) {
            if (timetable.value.isNotEmpty()) {
                val firstLesson = timetable.value.firstOrNull() ?: continue
                isSecondShift = firstLesson.schedule.startHour >= 13
                break
            }
        }
        EversityDatabase.registerClass(classID, classTeacherID, className, isSecondShift)
        EversityDatabase.registerClassTimetable(classID, classTimetable)
        EversityDatabase.registerManyPupils(pupilsArray)
        return true
    }

    /**
     * Registers teacher's timetable
     * @param userID ID of teacher
     * @param schoolsWebWrapper Initialized [SchoolsWebWrapper] (contains valid credentials)
     */
    suspend fun registerTeacherTimetable(userID: Int, schoolsWebWrapper: SchoolsWebWrapper): Boolean {
        if (!schoolsWebWrapper.validateCookies()) {
            val illegalArgumentException =
                IllegalArgumentException("Cookies are wrong (cookies validator returned false)")
            logger.error(illegalArgumentException)
            //TODO: Throw some kind of "not a class teacher exception"
            throw illegalArgumentException
        }
        val timetable = schoolsWebWrapper.fetchTeacherTimetable(userID)
        EversityDatabase.registerTeacherTimetable(userID, timetable)
        return true
    }
}