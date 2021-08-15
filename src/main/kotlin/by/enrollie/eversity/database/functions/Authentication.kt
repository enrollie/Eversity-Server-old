/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.data_classes.DayOfWeek
import by.enrollie.eversity.data_classes.TeacherLesson
import by.enrollie.eversity.database.tables.BannedTokens
import by.enrollie.eversity.database.tables.Classes
import by.enrollie.eversity.database.tables.TeachersTimetable
import by.enrollie.eversity.database.tables.Tokens
import by.enrollie.eversity.database.validTokensSet
import by.enrollie.eversity.exceptions.UserNotRegistered
import by.enrollie.eversity.tokenCacheValidityMinutes
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.Minutes
import java.util.*

/**
 * Issues new token for given user ID and saving it to database
 *
 * @param userID User ID to issue token
 * @return [String], containing issued token
 */
fun issueToken(userID: Int): String {
    var issuedToken = ""
    run {
        var newToken: UUID
        while (1 in 1..1) { //making sure that generated token is not seen anywhere
            newToken = UUID.randomUUID()
            val sameIssuedTokens = transaction {
                Tokens.select { Tokens.token eq newToken.toString() }.toList()
            }
            if (sameIssuedTokens.isNotEmpty())
                continue
            val sameBannedTokens = transaction {
                BannedTokens.select {
                    BannedTokens.token eq newToken.toString()
                }.toList()
            }
            if (sameBannedTokens.isNotEmpty())
                continue
            issuedToken = newToken.toString()
            break
        }
    }
    transaction {
        Tokens.insert {
            it[Tokens.userID] = userID
            it[Tokens.token] = issuedToken
        }
    }
    validTokensSet.add(Triple(userID, issuedToken, DateTime.now()))
    return issuedToken
}

/**
 * Invalidates all user tokens
 *
 * @param userID User ID to invalidate tokens
 * @return Count of invalidated tokens
 * @throws IllegalArgumentException Thrown, if no user with such ID is registered
 */
fun invalidateAllTokens(userID: Int, reason: String?): Int {
    if (!doesUserExist(userID)) {
        throw UserNotRegistered("User with ID $userID does not exist")
    }
    val tokensToInvalidate = transaction {
        Tokens.select {
            Tokens.userID eq userID
        }.toList()
    }
    val invalidationSize = tokensToInvalidate.size
    transaction {
        Tokens.deleteWhere {
            Tokens.userID eq userID
        }
        tokensToInvalidate.forEach { res ->
            BannedTokens.insert {
                it[BannedTokens.userID] = userID
                it[BannedTokens.token] = res[Tokens.token]
                it[banDate] = DateTime.now()
                it[BannedTokens.reason] = reason ?: "Unknown"
            }
        }
    }
    validTokensSet.removeIf {
        it.first == userID
    }
    return invalidationSize
}

/**
 * Finds access token in databases.
 *
 * @param userID ID of user
 * @param token Token to check
 * @return If token is found and it is not banned, returns (true, null). If token is found, but it is banned, returns (false, reason of ban). If token is not found, returns (false,null).
 */
fun checkToken(userID: Int, token: String): Pair<Boolean, String?> {
    if (validTokensSet.find {
            it.first == userID && it.second == token && Minutes.minutesBetween(
                it.third,
                DateTime.now()
            ).minutes <= tokenCacheValidityMinutes
        } != null) {
        return Pair(true, null)
    }
    val foundInValid = transaction {
        Tokens.select {
            (Tokens.userID eq userID) and
            (Tokens.token eq token)
        }.toList().isNotEmpty()
    }
    if (foundInValid) {
        validTokensSet.add(Triple(userID, token, DateTime.now()))
        return Pair(true, null)
    }
    return Pair(false, null)
}

/**
 * Invalidates given user token
 * @param userID User ID
 * @param token Token to invalidate
 * @param reason Reason of invalidation (if null, defaults to "Unknown")
 * @return True, if token was banned. False, if user is not found.
 */
fun invalidateSingleToken(userID: Int, token: String, reason: String?): Boolean {
    if (!doesUserExist(userID)) {
        return false
    }
    validTokensSet.removeIf {
        it.first == userID && it.second == token
    }
    transaction {
        Tokens.deleteWhere {
            (Tokens.userID eq userID) and
                    (Tokens.token eq token)
        }
        BannedTokens.insert {
            it[BannedTokens.userID] = userID
            it[BannedTokens.token] = token
            it[BannedTokens.reason] = reason ?: "Unknown"
            it[banDate] = DateTime.now()
        }
    }
    return true
}

fun validatePupilAccess(pupilID: Int, teacherID: Int): Boolean {
    require(doesUserExist(pupilID)) { "Pupil with ID $pupilID does not exist" }
    require(doesUserExist(teacherID)) { "Teacher with ID $teacherID does not exist" }
    try {
        val teacher = getTeacher(teacherID)
        val pupilClass = getPupilClass(pupilID)
        if (pupilClass != teacher.second)
            return false
        return true
    } catch (e: IllegalArgumentException) {
        return false
    }
}

fun batchValidatePupilAccess(pupilsList: List<Int>, teacherID: Int): List<Pair<Int, Boolean>> {
    val illegalAccessList = mutableListOf<Pair<Int, Boolean>>()
    pupilsList.forEach {
        illegalAccessList.add(Pair(it, validatePupilAccess(it, teacherID)))
    }
    return illegalAccessList
}

fun validateTeacherAccessToClass(teacherID: Int, classID: Int): Boolean {
    require(doesUserExist(teacherID)) { "Teacher with ID $teacherID does not exist" }
    require(doesClassExist(classID)) { "Class with ID $classID does not exist" }
    return transaction {
        val isClassTeacher =
            Classes.select { Classes.classTeacher eq teacherID }.toList().any { it[Classes.classID] == classID }
        if (isClassTeacher) {
            println("Class")
            println(teacherID)
            return@transaction true
        }
        val teacherTimetable = TeachersTimetable.select { TeachersTimetable.id eq teacherID }.toList().firstOrNull()
            ?: return@transaction false
        val dayTimetable =
            Json.decodeFromString<Pair<Array<TeacherLesson>?, Array<TeacherLesson>?>>(
                teacherTimetable[when (DayOfWeek.values()[DateTime.now().dayOfWeek - 1]) {
                    DayOfWeek.MONDAY -> TeachersTimetable.monday
                    DayOfWeek.TUESDAY -> TeachersTimetable.tuesday
                    DayOfWeek.WEDNESDAY -> TeachersTimetable.wednesday
                    DayOfWeek.THURSDAY -> TeachersTimetable.thursday
                    DayOfWeek.FRIDAY -> TeachersTimetable.friday
                    DayOfWeek.SATURDAY -> TeachersTimetable.saturday
                    else -> return@transaction false
                }]
            )
        val isFirstShiftTeacher = if (dayTimetable.first != null) {
            dayTimetable.first!!.firstOrNull()?.place == 1.toShort() && dayTimetable.first!!.firstOrNull()?.classID == classID
        } else false
        val isSecondShiftTeacher = if (dayTimetable.second != null) {
            dayTimetable.second!!.firstOrNull()?.place == 1.toShort() && dayTimetable.second!!.firstOrNull()?.classID == classID
        } else false
        return@transaction isFirstShiftTeacher || isSecondShiftTeacher
    }
}