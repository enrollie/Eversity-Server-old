/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database

import by.enrollie.eversity.data_classes.*
import by.enrollie.eversity.database.tables.*
import by.enrollie.eversity.exceptions.UserNotRegistered
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.Hours
import java.util.*
import kotlin.NoSuchElementException

/**
 * Temporary cache of valid access tokens.
 */
var validTokensList = mutableListOf<Triple<Int, String, DateTime>>()

/**
 * Interface to communicate with Eversity Database.
 * @author Pavel Matusevich
 */
object EversityDatabase {
    /**
     * Checks, whether user exists
     * @param userID ID of user
     * @return true, if user exists. false otherwise
     */
    fun doesUserExist(userID: Int): Boolean {
        val tos = transaction {
            Users.select { Users.id eq userID }.toList()
        }
        println(tos.joinToString())
        return tos.isNotEmpty()
    }

    /**
     * Checks, whether class exists.
     * @param classID ID of class
     *
     * @return True, if class exists. False otherwise.
     */
    fun doesClassExist(classID: Int): Boolean {
        return transaction {
            Classes.select { Classes.classID eq classID }.toList()
        }.isNotEmpty()
    }

    /**
     * Registers class teacher (does not check for credentials validity).
     * (does not register pupils, neither timetables)
     *
     * @param userID User ID of class teacher
     * @param fullName [Triple] First - First name, Second - Middle name, Third - Last name
     * @param cookies [Pair] First - csrftoken, second - sessionID
     * @param tokenAPI Token for Schools.by API
     * @param classID ID of class
     *
     * @return False, if user already exists. True, if operation succeed
     */
    fun registerClassTeacher(
        userID: Int,
        fullName: Triple<String, String, String>,
        cookies: Pair<String, String>,
        tokenAPI: String,
        classID: Int
    ): Boolean {
        if (doesUserExist(userID)) {
            return false
        }
        registerUser(userID, APIUserType.Teacher.name)
        transaction {
            Teachers.insert {
                it[id] = userID
                it[firstName] = fullName.first
                it[middleName] = fullName.second
                it[lastName] = fullName.third
                it[this.classID] = classID
            }
        }
        insertOrUpdateCredentials(userID, Triple(cookies.first, cookies.second, tokenAPI))
        return true
    }

    /**
     * Registers teacher (does not check for credentials validity). Does not register timetables
     *
     * @param userID User ID of class teacher
     * @param fullName [Triple] First - First name, Second - Middle name, Third - Last name
     * @param cookies [Pair] First - csrftoken, second - sessionID
     * @param tokenAPI Token for Schools.by API
     *
     */
    fun registerTeacher(
        userID: Int,
        fullName: Triple<String, String, String>,
        cookies: Pair<String, String>,
        tokenAPI: String
    ) {
        if (doesUserExist(userID)) {
            return
        }
        registerUser(userID, APIUserType.Teacher.name)
        transaction {
            Teachers.insert {
                it[id] = userID
                it[firstName] = fullName.first
                it[middleName] = fullName.second
                it[lastName] = fullName.third
                it[classID] = null
            }
        }
        insertOrUpdateCredentials(userID, Triple(cookies.first, cookies.second, tokenAPI))
        return
    }

    /**
     * Registers single pupil (does not register it's timetable)
     *
     * @param userID ID of pupil
     * @param name First - First name, Second - Last name
     * @param classID Class ID of given pupil
     * @see registerManyPupils
     * @return False, if pupil already exists. True, if operation succeed
     * @throws IllegalArgumentException Thrown, if pupil's class is not yet registered.
     * @throws UnknownError Thrown, if registerUser() was called, but [doesUserExist] returned false (user does not exist)
     */
    fun registerPupil(userID: Int, name: Pair<String, String>, classID: Int): Boolean {
        if (doesUserExist(userID)) {
            return false
        }
        if (!doesClassExist(classID)) {
            throw IllegalArgumentException("Class with ID $classID does not yet exist. Pupil with ID $userID cannot be registered.")
        }
        registerUser(userID, APIUserType.Pupil.name)
        if (!doesUserExist(userID)) {
            throw UnknownError("registerUser() was called, but user does not exist.")
        }
        transaction {
            Pupils.insert {
                it[id] = userID
                it[this.classID] = classID
                it[firstName] = name.first
                it[lastName] = name.second
            }
        }
        return true
    }

    /**
     * Registers many pupils at once. (does not register their timetables).
     * Ignores pupil if it already exists.
     * @param array Array, containing [Pupil]s to register
     */
    fun registerManyPupils(array: Array<Pupil>) {
        array.forEach { pupil ->
            registerPupil(pupil.id, Pair(pupil.firstName, pupil.lastName), pupil.classID)
        }
    }

    /**
     * Registers (or, overwrites, if exists) timetable for class
     *
     * @param classID ID of pupil
     * @param daysMap Map of <[DayOfWeek], [TimetableDay]>. MUST contain at least six elements (days) AND less than 8 elements
     *
     * @throws IllegalArgumentException Thrown, if [daysMap] does not contain some day of week (except SUNDAY) OR it has less than six elements
     */
    fun registerClassTimetable(classID: Int, daysMap: Map<DayOfWeek, TimetableDay>) {
        if (daysMap.size < 6 || daysMap.size > 7) {
            println(Json.encodeToString(daysMap))
            throw IllegalArgumentException("daysArray size is less than six OR more than 7")
        }
        for (i in 0 until 6) {
            when (i) {
                DayOfWeek.MONDAY.ordinal -> {
                    if (!daysMap.containsKey(DayOfWeek.MONDAY))
                        throw IllegalArgumentException("daysArray does not contain Monday")
                }
                DayOfWeek.TUESDAY.ordinal -> {
                    if (!daysMap.containsKey(DayOfWeek.TUESDAY))
                        throw IllegalArgumentException("daysArray does not contain Tuesday")
                }
                DayOfWeek.WEDNESDAY.ordinal -> {
                    if (!daysMap.containsKey(DayOfWeek.WEDNESDAY))
                        throw IllegalArgumentException("daysArray does not contain Wednesday")
                }
                DayOfWeek.THURSDAY.ordinal -> {
                    if (!daysMap.containsKey(DayOfWeek.THURSDAY))
                        throw IllegalArgumentException("daysArray does not contain Thursday")
                }
                DayOfWeek.FRIDAY.ordinal -> {
                    if (!daysMap.containsKey(DayOfWeek.FRIDAY))
                        throw IllegalArgumentException("daysArray does not contain Friday")
                }
                DayOfWeek.SATURDAY.ordinal -> {
                    if (!daysMap.containsKey(DayOfWeek.SATURDAY))
                        throw IllegalArgumentException("daysArray does not contain Saturday")
                }
            }
        }
        transaction {
            ClassTimetables.insert {
                it[id] = classID
                it[monday] = Json.encodeToString(daysMap[DayOfWeek.MONDAY])
                it[tuesday] = Json.encodeToString(daysMap[DayOfWeek.TUESDAY])
                it[wednesday] = Json.encodeToString(daysMap[DayOfWeek.WEDNESDAY])
                it[thursday] = Json.encodeToString(daysMap[DayOfWeek.THURSDAY])
                it[friday] = Json.encodeToString(daysMap[DayOfWeek.FRIDAY])
                it[saturday] = Json.encodeToString(daysMap[DayOfWeek.SATURDAY])
            }
        }
        return
    }

    /**
     * Issues new token for given user ID and saving it to database
     *
     * @param userID User ID to issue token
     * @return [String], containing issued token
     */
    fun issueToken(userID: Int): String {
        var issuedToken: String = ""
        run {
            var newToken: UUID = UUID.randomUUID()
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
                it[token] = issuedToken
            }
        }
        validTokensList.add(Triple(userID, issuedToken, DateTime.now()))
        return issuedToken
    }

    /**
     * Invalidates all user tokens
     *
     * @param userID User ID to invalidate tokens
     * @return Count of invalidated tokens
     * @throws IllegalArgumentException Thrown, if no user with such ID is registered
     */
    fun invalidateTokens(userID: Int, reason: String?): Int {
        if (!doesUserExist(userID))
            throw IllegalArgumentException("Database does not contain user with such user ID ($userID)")
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
                    it[token] = res[Tokens.token]
                    it[banDate] = DateTime.now()
                    it[BannedTokens.reason] = reason ?: "Unknown"
                }
            }
        }
        validTokensList.removeIf {
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
        if (validTokensList.find {
                it.first == userID && it.second == token && Hours.hoursBetween(it.third, DateTime.now())
                    .isLessThan(Hours.hours(1))
            } != null) {
            return Pair(true, null)
        }
        val foundInValid = transaction {
            Tokens.select {
                Tokens.userID eq userID
                Tokens.token eq token
            }.toList().isNotEmpty()
        }
        if (foundInValid) {
            validTokensList.add(Triple(userID, token, DateTime.now()))
            return Pair(true, null)
        }
        val foundBanned = transaction {
            BannedTokens.select {
                BannedTokens.token eq token
            }.toList()
        }
        if (foundBanned.isEmpty()) {
            return Pair(false, null)
        }
        val banReason = foundBanned.firstOrNull() ?: return Pair(false, null)
        //TODO: Add logging
        return Pair(false, banReason.getOrNull(BannedTokens.reason))
    }

    private fun registerUser(userID: Int, type: String) {
        transaction {
            Users.insert {
                it[id] = userID
                it[Users.type] = type
            }
        }
    }

    /**
     * Obtains user credentials from database
     *
     * @param userID ID of user to get credentials
     * @return Triple, made of (csrftoken?, sessionid?, APIToken)
     * @throws IllegalArgumentException Thrown, if user is not found
     * @throws NoSuchElementException Thrown, if no credentials were found OR they are outdated (in any of cases, you are required to re-register credentials)
     * @throws IllegalStateException Thrown, if more than two credentials sets have been found
     */
    fun obtainCredentials(userID: Int): Triple<String?, String?, String> {
        if (!doesUserExist(userID)) {
            throw IllegalArgumentException("Credentials not found for user ID $userID")
        }
        val credentialsList = transaction {
            Credentials.select {
                Credentials.id eq userID
            }.toList()
        }
        if (credentialsList.size > 1) {
            //TODO: Notify system administrator to check database consistency
            throw IllegalArgumentException("More than one set of credentials have been found")
        }
        if (credentialsList.isEmpty()) {
            throw NoSuchElementException("Credentials list for user ID $userID is empty")
        }
        val credentials =
            credentialsList.firstOrNull() ?: throw NoSuchElementException("Credentials first element is null")
        return Triple(
            credentials[Credentials.csrfToken],
            credentials[Credentials.sessionID],
            credentials[Credentials.token]
        )
    }

    /**
     * Saves (or updates existing) credentials for given user ID.
     *
     * @param userID ID of user
     * @param credentials Triple, containing (csrftoken?, sessionid?, APItoken)
     * @throws IllegalArgumentException Thrown, if user does not exist
     */
    fun insertOrUpdateCredentials(userID: Int, credentials: Triple<String?, String?, String>) {
        if (!doesUserExist(userID)) {
            throw IllegalArgumentException("User does not exist!")
        }
        val deletedCredentials = transaction {
            Credentials.deleteWhere {
                Credentials.id eq userID
            }
        }
        //TODO: Add logging
        transaction {
            Credentials.insert {
                it[id] = userID
                it[csrfToken] = credentials.first
                it[sessionID] = credentials.second
                it[token] = credentials.third
            }
        }
    }

    /**
     * Registers (or, updates) school class (does not register class teacher, pupils, timetables etc.)
     *
     * @param classID ID of class
     * @param classTeacherID ID of class teacher (does not need to be registered)
     */
    fun registerClass(classID: Int, classTeacherID: Int) {
        if (doesClassExist(classID))
            return
        transaction {
            Classes.insert {
                it[this.classID] = classID
                it[this.classTeacher] = classTeacherID
            }
        }
        return
    }

    /**
     * Registers (or, overwrites, if exists) timetable for teacher
     *
     * @param teacherID ID of pupil
     * @param daysMap Map of <[DayOfWeek], [TimetableDay]>. MUST contain at least six elements (days) AND less than 8 elements
     *
     * @throws IllegalArgumentException Thrown, if [daysMap] does not contain some day of week (except SUNDAY) OR it has less than six elements
     */
    fun registerTeacherTimetable(teacherID: Int, daysMap: Map<DayOfWeek, Array<TeacherLesson>>) {
        for (i in 0 until 6) {
            when (i) {
                DayOfWeek.MONDAY.ordinal -> {
                    if (!daysMap.containsKey(DayOfWeek.MONDAY))
                        throw IllegalArgumentException("daysArray does not contain Monday")
                }
                DayOfWeek.TUESDAY.ordinal -> {
                    if (!daysMap.containsKey(DayOfWeek.TUESDAY))
                        throw IllegalArgumentException("daysArray does not contain Tuesday")
                }
                DayOfWeek.WEDNESDAY.ordinal -> {
                    if (!daysMap.containsKey(DayOfWeek.WEDNESDAY))
                        throw IllegalArgumentException("daysArray does not contain Wednesday")
                }
                DayOfWeek.THURSDAY.ordinal -> {
                    if (!daysMap.containsKey(DayOfWeek.THURSDAY))
                        throw IllegalArgumentException("daysArray does not contain Thursday")
                }
                DayOfWeek.FRIDAY.ordinal -> {
                    if (!daysMap.containsKey(DayOfWeek.FRIDAY))
                        throw IllegalArgumentException("daysArray does not contain Friday")
                }
                DayOfWeek.SATURDAY.ordinal -> {
                    if (!daysMap.containsKey(DayOfWeek.SATURDAY))
                        throw IllegalArgumentException("daysArray does not contain Saturday")
                }
            }
        }
        transaction {
            TeachersTimetable.insert {
                it[id] = teacherID
                it[monday] = Json.encodeToString(daysMap[DayOfWeek.MONDAY])
                it[tuesday] = Json.encodeToString(daysMap[DayOfWeek.TUESDAY])
                it[wednesday] = Json.encodeToString(daysMap[DayOfWeek.WEDNESDAY])
                it[thursday] = Json.encodeToString(daysMap[DayOfWeek.THURSDAY])
                it[friday] = Json.encodeToString(daysMap[DayOfWeek.FRIDAY])
                it[saturday] = Json.encodeToString(daysMap[DayOfWeek.SATURDAY])
            }
        }
    }

    /**
     * Determines, whether you should update class data
     * @param classID ID of class
     * @return True, if you SHOULD (!) update class data. False otherwise
     */
    fun shouldUpdateClass(classID: Int): Boolean {
        val classExisting = doesClassExist(classID)
        val existingPupils = transaction {
            Pupils.select {
                Pupils.classID eq classID
            }.toList().isNotEmpty()
        }
        val existingTimetable = transaction {
            ClassTimetables.select {
                ClassTimetables.id eq classID
            }.toList().isNotEmpty()
        }
        if (classExisting) {
            if (existingPupils) {
                if (existingTimetable)
                    return false
            }
        }
        return true
    }

    fun getUserType(userID: Int): APIUserType {
        val type = transaction {
            Users.select {
                Users.id eq userID
            }.toList().firstOrNull()
        } ?: throw UserNotRegistered("User with ID $userID not found in database.")
        return APIUserType.valueOf(type[Users.type])
    }

    fun isTimetableValid(userID: Int): Boolean {
        if (!doesUserExist(userID))
            throw IllegalArgumentException()
        return when (getUserType(userID)) {
            APIUserType.Teacher -> {
                transaction {
                    TeachersTimetable.select {
                        TeachersTimetable.id eq userID
                    }.toList().isNotEmpty()
                }
            }
            APIUserType.Pupil -> {
                false
            }
            else -> {
                false
            }
        }
    }

    fun getPupilClass(userID: Int): Int {
        if (!doesUserExist(userID)) {
            throw IllegalArgumentException("User with ID $userID not found in database.")
        }
        val user = transaction {
            Users.select {
                Users.id eq userID
            }.toList().firstOrNull()
        } ?: throw IllegalArgumentException("User with ID $userID not found in database.")
        //TODO: Make this done.
        TODO("To be implemented in v0.0.2")
//        if(user[Users.type] != "Pupil")
        return 5
    }
}