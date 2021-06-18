/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.controllers

import by.enrollie.eversity.database.EversityDatabase
import by.enrollie.eversity.exceptions.AuthorizationUnsuccessful
import by.enrollie.eversity.exceptions.UserNotRegistered
import by.enrollie.eversity.schools_by.SchoolsAPIClient
import by.enrollie.eversity.schools_by.SchoolsWebWrapper
import by.enrollie.eversity.security.EversityJWT
import io.ktor.util.*
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.Logger

class AuthController {

    private val registrar = Registrar()

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
     * Registers user and all it's data in database
     *
     */
    suspend fun registerUser(username: String, password: String): String {
        val schoolsAPI = SchoolsAPIClient()
        val schoolsWeb = SchoolsWebWrapper()
        val schoolsToken = schoolsAPI.getAPIToken(username, password) ?: throw AuthorizationUnsuccessful()
        val userData = schoolsAPI.getCurrentUserData()
        val userID = userData["id"].toString().toInt()
        if (EversityDatabase.doesUserExist(userID)) {
            return loginUser(username, password)
        }
        val credentials = schoolsWeb.login(username, password)
        when (userData["type"]?.jsonPrimitive?.content) {
            "Pupil" -> {
                val pupilData = schoolsAPI.getPupilInfo(userID.toString())
                val classID: Int = pupilData["class_id"]?.jsonPrimitive?.content?.toInt() ?: {
                    val unknownError =
                        UnknownError("Class ID wasn't found in pupilData. Pupil data content: \'$pupilData\'")
                    logger.error(unknownError)
                    throw unknownError
                }.toString().toInt()
                val className = pupilData["class_name"]?.jsonPrimitive?.contentOrNull ?: "UNKNOWN"
                if (!EversityDatabase.doesClassExist(classID)) {
                    try {
                        registrar.registerClass(classID, className, schoolsWeb)
                    } catch (e: IllegalArgumentException) {
                        logger.error(e)
                        throw UnknownError("Schools web wrapper has invalid cookies")
                    }
                }
                EversityDatabase.registerPupil(
                    userID,
                    Pair(
                        userData["first_name"]?.jsonPrimitive?.content.toString(),
                        userData["last_name"]?.jsonPrimitive?.content.toString()
                    ),
                    classID
                )
                EversityDatabase.insertOrUpdateCredentials(
                    userID,
                    Triple(credentials.first, credentials.second, schoolsToken)
                )
                val eversityToken = EversityDatabase.issueToken(userID)
                return EversityJWT.instance.sign(userID.toString(), eversityToken)
            }
            "Teacher" -> {
                val classStr = schoolsWeb.fetchClassForCurrentUser()
                if (classStr != null) {
                    var className = userData["short_info"]?.jsonPrimitive?.contentOrNull ?: "UNKNOWN"
                    kotlin.run {
                        var numPosition: Int = -1
                        className = className.filterIndexed { index, c ->
                            if (c.digitToIntOrNull() != null) {
                                numPosition = index
                                return@filterIndexed true
                            }
                            if (numPosition != -1)
                                return@filterIndexed true
                            false
                        }
                        className = className.replace("-го", "")
                    }
                    registrar.registerClass(classStr, className, schoolsWeb)
                    EversityDatabase.registerTeacher(
                        userID,
                        Triple(
                            userData["first_name"]?.jsonPrimitive?.content.toString(),
                            userData["father_name"]?.jsonPrimitive?.content.toString(),
                            userData["last_name"]?.jsonPrimitive?.content.toString()
                        ),
                        credentials,
                        schoolsToken,
                        classStr
                    )
                } else {
                    EversityDatabase.registerTeacher(
                        userID,
                        Triple(
                            userData["first_name"]?.jsonPrimitive?.content.toString(),
                            userData["father_name"]?.jsonPrimitive?.content.toString(),
                            userData["last_name"]?.jsonPrimitive?.content.toString()
                        ),
                        credentials,
                        schoolsToken
                    )
                }
                registrar.registerTeacherTimetable(userID, schoolsWeb)
                val eversityToken = EversityDatabase.issueToken(userID)
                return EversityJWT.instance.sign(userID.toString(), eversityToken)
            }
            else -> {
                logger.error("Unknown user type! userData JSON: \'$userData\'")
                throw UnknownError("Unknown user type! userData JSON: \'$userData\'")
            }
        }
    }

    suspend fun loginUser(username: String, password: String): String {
        val schoolsAPI = SchoolsAPIClient()
        val schoolsWeb = SchoolsWebWrapper()
        val schoolsToken = schoolsAPI.getAPIToken(username, password) ?: throw AuthorizationUnsuccessful()
        val userData = schoolsAPI.getCurrentUserData()
        val userID = userData["id"].toString().toInt()
        if (!EversityDatabase.doesUserExist(userID)) {
            throw UserNotRegistered("User with ID $userID is not registered")
        }
        try {
            val credentials = EversityDatabase.obtainCredentials(userID)
            if (credentials.first != null && credentials.second != null) {
                if (!schoolsWeb.validateCookies(Pair(credentials.first!!, credentials.second!!), true)) {
                    logger.debug("Credentials of user with ID $userID were invalid, recreating...")
                    val newCredentials = schoolsWeb.login(username, password)
                    EversityDatabase.insertOrUpdateCredentials(
                        userID,
                        Triple(newCredentials.first, newCredentials.second, schoolsToken)
                    )
                }
                if (userData["type"]?.jsonPrimitive?.content.toString() == "Teacher") {
                    val classStr = schoolsWeb.fetchClassForCurrentUser()
                    var className = userData["short_info"]?.jsonPrimitive?.contentOrNull ?: "UNKNOWN"
                    kotlin.run {
                        if (className == "UNKNOWN")
                            return@run
                        var numPosition: Int = -1
                        className = className.filterIndexed { index, c ->
                            if (c.digitToIntOrNull() != null) {
                                numPosition = index
                                return@filterIndexed true
                            }
                            if (numPosition != -1)
                                return@filterIndexed true
                            false
                        }
                        className = className.replace("-го", "")
                    }
                    if (classStr != null)
                        registrar.registerClass(classStr, className, schoolsWeb)
                }
            }
            if (userData["type"]?.jsonPrimitive?.content.toString() == "Teacher") {
                registrar.registerTeacherTimetable(userID, schoolsWeb)
            }
        } catch (e: IllegalArgumentException) {
            logger.error(e)
            throw UnknownError()
        } catch (e: NoSuchElementException) {
            val newCredentials = schoolsWeb.login(username, password)
            EversityDatabase.insertOrUpdateCredentials(
                userID,
                Triple(newCredentials.first, newCredentials.second, schoolsToken)
            )
        } catch (e: IllegalStateException) {
            val newCredentials = schoolsWeb.login(username, password)
            EversityDatabase.insertOrUpdateCredentials(
                userID,
                Triple(newCredentials.first, newCredentials.second, schoolsToken)
            )
        }
        val token = EversityDatabase.issueToken(userID)
        return EversityJWT.instance.sign(userID.toString(), token)
    }
}