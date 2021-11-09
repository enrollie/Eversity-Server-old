/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.controllers

import by.enrollie.eversity.data_classes.APIUserType
import by.enrollie.eversity.data_classes.User
import by.enrollie.eversity.database.functions.getPupilClass
import by.enrollie.eversity.database.functions.obtainCredentials
import by.enrollie.eversity.database.functions.registerClassTimetable
import by.enrollie.eversity.database.functions.registerTeacherTimetable
import by.enrollie.eversity.schools_by.SchoolsWebWrapper

class DataController {

    /**
     * Updates user data
     * @param user User whose data needs to be updated
     * @throws IllegalStateException Thrown, if user's cookies are invalid. In this case, access tokens invalidation advised
     */
    suspend fun updateUser(user: User) {
        require(user.isValid()) { "User is not valid" }
        val wrapper = SchoolsWebWrapper()
        when (user.type) {
            APIUserType.Pupil -> {
                val credentials = try {
                    obtainCredentials(user.id)
                } catch (e: NoSuchElementException) {
                    wrapper.destroy()
                    //TODO: Add finding of appropriate credentials in other user's
                    throw IllegalStateException("Cookies are invalid. Re-login required to update data.")
                }
                val validity = wrapper.validateCookies(
                    Pair(credentials.first.toString(), credentials.second.toString()),
                    changeInternal = true
                )
                if (!validity) {
                    wrapper.destroy()
                    throw IllegalStateException("Cookies are invalid. Re-login required to update data.")
                }
                val classID = getPupilClass(user.id)
                val classTimetable = wrapper.fetchClassTimetable(classID)
                wrapper.destroy()
                registerClassTimetable(classID, classTimetable)
            }
            APIUserType.Teacher -> {
                val credentials = try {
                    obtainCredentials(user.id)
                } catch (e: NoSuchElementException) {
                    wrapper.destroy()
                    //TODO: Add finding of appropriate credentials in other user's
                    throw IllegalStateException("Cookies are invalid. Re-login required to update data.")
                }
                val validity = wrapper.validateCookies(
                    Pair(credentials.first.toString(), credentials.second.toString()),
                    changeInternal = true
                )
                if (!validity) {
                    wrapper.destroy()
                    throw IllegalStateException("Cookies are invalid. Re-login required to update data.")
                }
                val timetable = wrapper.fetchTeacherTimetable(user.id)
                registerTeacherTimetable(user.id, timetable)
                wrapper.destroy()
            }
            else -> {
                return
            }
        }
    }
}