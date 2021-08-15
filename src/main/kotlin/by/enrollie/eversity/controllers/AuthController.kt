/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.controllers

import by.enrollie.eversity.data_classes.APIUserType
import by.enrollie.eversity.database.functions.*
import by.enrollie.eversity.exceptions.AuthorizationUnsuccessful
import by.enrollie.eversity.exceptions.UserNotRegistered
import by.enrollie.eversity.schools_by.SchoolsWebWrapper
import by.enrollie.eversity.security.EversityJWT
import io.ktor.util.*
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
     * @throws AuthorizationUnsuccessful Thrown, if Schools.by rejected credentials
     * @return Eversity access token
     */
    suspend fun registerUser(username: String, password: String): String {
        val schoolsWeb = SchoolsWebWrapper()
        val credentials = schoolsWeb.login(username, password)
        val userID = schoolsWeb.authenticatedUserID ?: throw AuthorizationUnsuccessful()
        if (doesUserExist(userID)) {
            return loginUser(username, password)
        }
        when (schoolsWeb.userType) {
            APIUserType.Pupil -> {
                val (classID, className) = schoolsWeb.getPupilClass(userID)
                if (!doesClassExist(classID)) {
                    try {
                        registrar.registerClass(classID, className, schoolsWeb)
                    } catch (e: IllegalArgumentException) {
                        logger.error(e)
                        throw UnknownError("Schools web wrapper has invalid cookies")
                    }
                }
                registerPupil(
                    userID,
                    schoolsWeb.getUserName(userID),
                    classID
                )
                insertOrUpdateCredentials(
                    userID,
                    Triple(credentials.first, credentials.second, "")
                )
                val eversityToken = issueToken(userID)
                return EversityJWT.instance.sign(userID.toString(), eversityToken)
            }
            APIUserType.Teacher -> {
                val classData = schoolsWeb.fetchClassForCurrentUser()
                if (classData != null) {
                    registrar.registerClass(classData.first, classData.second, schoolsWeb)
                    val name = schoolsWeb.getUserName(userID)
                    registerTeacher(
                        userID,
                        Triple(
                            name.first,
                            name.second.split(" ")[1],
                            name.second.split(" ").first()
                        ),
                        credentials,
                        "",
                        classData.first
                    )
                } else {
                    val name = schoolsWeb.getUserName(userID)
                    registerTeacher(
                        userID,
                        Triple(
                            name.first,
                            name.second.split(" ")[1],
                            name.second.split(" ").first()
                        ),
                        credentials,
                        ""
                    )
                }
                registrar.registerTeacherTimetable(userID, schoolsWeb)
                val eversityToken = issueToken(userID)
                return EversityJWT.instance.sign(userID.toString(), eversityToken)
            }
            APIUserType.Parent -> {
                registerParent(userID)
                insertOrUpdateCredentials(userID, Triple(credentials.first, credentials.second, ""))
                val eversityToken = issueToken(userID)
                return EversityJWT.instance.sign(userID.toString(), eversityToken)
            }
            APIUserType.Administration -> { //Not possible, as on website (without deep parsing) they are indistinguishable from regular teachers, but still
                val name = schoolsWeb.getUserName(userID)
                registerAdministration(
                    userID,
                    Triple(
                        name.first,
                        name.second.split(" ")[1],
                        name.second.split(" ").first()
                    ),
                    credentials,
                    ""
                )
                registrar.registerTeacherTimetable(userID, schoolsWeb)
                val eversityToken = issueToken(userID)
                return EversityJWT.instance.sign(userID.toString(), eversityToken)
            }
            else -> {
                logger.error("Unknown user type! userData JSON: \'${schoolsWeb.userType}\'")
                throw UnknownError("Unknown user type! userData JSON: \'${schoolsWeb.userType}\'")
            }
        }
    }

    suspend fun loginUser(username: String, password: String): String {
        val schoolsWeb = SchoolsWebWrapper()
        val credentials = schoolsWeb.login(username, password)
        val userID = schoolsWeb.authenticatedUserID ?: throw AuthorizationUnsuccessful()
        if (!doesUserExist(userID)) {
            throw UserNotRegistered("User with ID $userID is not registered")
        }
        try {
            val prevCredentials = obtainCredentials(userID)
            if (prevCredentials.first != null && prevCredentials.second != null) {
                if (!schoolsWeb.validateCookies(Pair(prevCredentials.first!!, prevCredentials.second!!), true)) {
                    logger.debug("Credentials of user with ID $userID were invalid, recreating...")
                    insertOrUpdateCredentials(
                        userID,
                        Triple(credentials.first, credentials.second, "")
                    )
                }
                if (schoolsWeb.userType == APIUserType.Teacher) {
                    val classData = schoolsWeb.fetchClassForCurrentUser()
                    if (classData != null)
                        registrar.registerClass(classData.first, classData.second, schoolsWeb)
                }
            }
            if (schoolsWeb.userType == APIUserType.Teacher) {
                registrar.registerTeacherTimetable(userID, schoolsWeb)
            }
        } catch (e: IllegalArgumentException) {
            logger.error(e)
            throw UnknownError()
        } catch (e: NoSuchElementException) {
            val newCredentials = schoolsWeb.login(username, password)
            insertOrUpdateCredentials(
                userID,
                Triple(newCredentials.first, newCredentials.second, "")
            )
        } catch (e: IllegalStateException) {
            val newCredentials = schoolsWeb.login(username, password)
            insertOrUpdateCredentials(
                userID,
                Triple(newCredentials.first, newCredentials.second, "")
            )
        }
        val token = issueToken(userID)
        return EversityJWT.instance.sign(userID.toString(), token)
    }
}