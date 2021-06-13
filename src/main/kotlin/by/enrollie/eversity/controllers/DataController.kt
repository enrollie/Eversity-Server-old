/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.controllers

import by.enrollie.eversity.data_classes.APIUserType
import by.enrollie.eversity.data_classes.User
import by.enrollie.eversity.database.EversityDatabase
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
                    EversityDatabase.obtainCredentials(user.id)
                } catch (e: NoSuchElementException) {
                    //TODO: Add finding of appropriate credentials in other user's
                    throw IllegalStateException("Cookies are invalid. Re-login required to update data.")
                }
                val validity = wrapper.validateCookies(
                    Pair(credentials.first.toString(), credentials.second.toString()),
                    changeInternal = true
                )
                if (!validity) {
                    throw IllegalStateException("Cookies are invalid. Re-login required to update data.")
                }
                val classID = EversityDatabase.getPupilClass(user.id)
                val classTimetable = wrapper.fetchClassTimetable(classID)
                EversityDatabase.registerClassTimetable(classID, classTimetable)
            }
            APIUserType.Teacher -> {
                val credentials = try {
                    EversityDatabase.obtainCredentials(user.id)
                } catch (e: NoSuchElementException) {
                    //TODO: Add finding of appropriate credentials in other user's
                    throw IllegalStateException("Cookies are invalid. Re-login required to update data.")
                }
                val validity = wrapper.validateCookies(
                    Pair(credentials.first.toString(), credentials.second.toString()),
                    changeInternal = true
                )
                if (!validity) {
                    throw IllegalStateException("Cookies are invalid. Re-login required to update data.")
                }
                val timetable = wrapper.fetchTeacherTimetable(user.id)
                EversityDatabase.registerTeacherTimetable(user.id, timetable)
            }
            else -> {
                return
            }
        }
    }
}