/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.controllers

import by.enrollie.eversity.data_classes.*
import by.enrollie.eversity.database.functions.*
import by.enrollie.eversity.exceptions.AuthorizationUnsuccessful
import by.enrollie.eversity.exceptions.NotAllPupilsRegistered
import by.enrollie.eversity.security.EversityJWT
import com.neitex.SchoolsByParser
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object AuthController {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Registers user and all it's data in database
     * @return Eversity access token
     */
    private suspend fun registerUser(username: String, password: String): Result<String> {
        logger.debug("Beginning registering process for username \'$username\'...")
        val credentials = SchoolsByParser.AUTH.getLoginCookies(username, password)
            .fold(onSuccess = { it }, onFailure = {
                return if (it is com.neitex.AuthorizationUnsuccessful)
                    Result.failure(AuthorizationUnsuccessful())
                else Result.failure(it)
            })
        val userID = SchoolsByParser.USER.getUserIDFromCredentials(credentials)
            .fold(onSuccess = { it }, onFailure = { return Result.failure(it) })
        val userInfo = SchoolsByParser.USER.getBasicUserInfo(userID, credentials)
            .fold(onSuccess = { it }, onFailure = { return Result.failure(it) })
        logger.debug("Obtained user info; userID: $userID; userinfo: $userInfo")
        when (userInfo.type.toUserType()) {
            UserType.Pupil -> {
                val pupilSchoolClass =
                    SchoolsByParser.PUPIL.getPupilClass(userID, credentials)
                        .fold(onSuccess = { it }, onFailure = { return Result.failure(it) })
                if (!doesClassExist(pupilSchoolClass.id)) {
                    logger.debug("Registering class \'$pupilSchoolClass\'...")
                    val pupilsArray =
                        SchoolsByParser.CLASS.getPupilsList(pupilSchoolClass.id, credentials)
                            .fold(onSuccess = {
                                it.map { pupil ->
                                    Pupil(
                                        pupil.id,
                                        pupil.name.firstName,
                                        pupil.name.middleName,
                                        pupil.name.lastName,
                                        pupil.classID
                                    )
                                }.toTypedArray()
                            }, onFailure = { return Result.failure(it) })
                    val teacherTimetable =
                        SchoolsByParser.TEACHER.getTimetable(pupilSchoolClass.classTeacherID, credentials)
                            .fold(onSuccess = { it }, onFailure = { return Result.failure(it) })
                    val teacherProfile =
                        SchoolsByParser.USER.getBasicUserInfo(pupilSchoolClass.classTeacherID, credentials)
                            .fold(onSuccess = { it }, onFailure = { return Result.failure(it) })
                    val classTimetable =
                        SchoolsByParser.CLASS.getTimetable(pupilSchoolClass.id, credentials, guessShift = true)
                            .fold(onSuccess = { it }, onFailure = { return Result.failure(it) })
                    registerTeacher(
                        teacherProfile.id,
                        UserName.fromParserName(teacherProfile.name),
                        null,
                        TwoShiftsTimetable(teacherTimetable)
                    )
                    registerClass(
                        pupilSchoolClass.id,
                        pupilSchoolClass.classTeacherID,
                        pupilSchoolClass.classTitle,
                        classTimetable.first!!, Timetable(classTimetable.second)
                    )
                    registerManyPupils(pupilsArray)
                    logger.debug("Registered class \'$pupilSchoolClass\' and it's teacher \'$teacherProfile\'")
                }
                val token = issueToken(userID)
                return Result.success(EversityJWT.instance.sign(userID.toString(), token))
            }
            UserType.Teacher, UserType.Administration -> {
                val timetable = SchoolsByParser.TEACHER.getTimetable(userID, credentials)
                    .fold(onSuccess = { it }, onFailure = { return Result.failure(it) })
                val classData = SchoolsByParser.TEACHER.getClassForTeacher(userID, credentials)
                    .fold(onSuccess = { it }, onFailure = { return Result.failure(it) })
                if (classData != null) {
                    val pupilsArray =
                        SchoolsByParser.CLASS.getPupilsList(classData.id, credentials)
                            .fold(onSuccess = {
                                it.map { pupil ->
                                    Pupil(
                                        pupil.id,
                                        pupil.name.firstName,
                                        pupil.name.middleName,
                                        pupil.name.lastName,
                                        pupil.classID
                                    )
                                }.toTypedArray()
                            }, onFailure = { return Result.failure(it) })
                    val classTimetable =
                        SchoolsByParser.CLASS.getTimetable(classData.id, credentials, guessShift = true)
                            .fold(onSuccess = { it }, onFailure = { return Result.failure(it) })
                    registerTeacher(
                        userID,
                        UserName.fromParserName(userInfo.name),
                        Pair(credentials.csrfToken, credentials.sessionID),
                        TwoShiftsTimetable(timetable),
                        isAdministration = userInfo.type.toUserType() == UserType.Administration
                    )
                    registerClass(
                        classData.id,
                        userID,
                        classData.classTitle,
                        classTimetable.first!!, Timetable(classTimetable.second)
                    )
                    registerManyPupils(pupilsArray)
                } else {
                    registerTeacher(
                        userID,
                        UserName.fromParserName(userInfo.name),
                        Pair(credentials.csrfToken, credentials.sessionID),
                        TwoShiftsTimetable(timetable)
                    )
                }
                val eversityToken = issueToken(userID)
                return Result.success(EversityJWT.instance.sign(userID.toString(), eversityToken))
            }
            UserType.Parent -> {
                val pupils = SchoolsByParser.PARENT.getPupils(userID, credentials)
                    .fold(onSuccess = { it }, onFailure = { return Result.failure(it) })
                val notRegisteredPupils = getNonExistentPupilsIDs(pupils.toPupilsList())
                if (notRegisteredPupils.isNotEmpty())
                    return Result.failure(NotAllPupilsRegistered(notRegisteredPupils))
                registerParent(
                    userID,
                    UserName.fromParserName(userInfo.name),
                    Pair(credentials.csrfToken, credentials.sessionID)
                )
                assignPupilsToParents(pupils.map { Pair(it.id, userID) })
                val eversityToken = issueToken(userID)
                return Result.success(EversityJWT.instance.sign(userID.toString(), eversityToken))
            }
            else -> {
                return Result.failure(UnknownError("Schools.by parser returned impossible user type: ${userInfo.type}"))
            }
        }
    }

    suspend fun loginUser(username: String, password: String): Result<Pair<String, UserID>> {
        val credentials = SchoolsByParser.AUTH.getLoginCookies(username, password)
            .fold(onSuccess = { it }, onFailure = {
                return if (it is com.neitex.AuthorizationUnsuccessful)
                    Result.failure(AuthorizationUnsuccessful())
                else Result.failure(it)
            })
        val userID = SchoolsByParser.USER.getUserIDFromCredentials(credentials)
            .fold(onSuccess = { it }, onFailure = { return Result.failure(it) })
        if (!doesUserExist(userID)) {
            logger.debug("User with ID $userID is not registered, registering from login.")
            return registerUser(username, password).map { Pair(it, userID) }
        }
        recordSchoolsByCredentials(userID, Pair(credentials.csrfToken, credentials.sessionID))
        val token = issueToken(userID)
        return Result.success(Pair(EversityJWT.instance.sign(userID.toString(), token), userID))
    }
}
