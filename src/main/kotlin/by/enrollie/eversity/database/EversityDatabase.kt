/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

@file:Suppress("DuplicatedCode", "DuplicatedCode", "DuplicatedCode", "DuplicatedCode")

package by.enrollie.eversity.database

import by.enrollie.eversity.data_classes.*
import by.enrollie.eversity.database.tables.*
import by.enrollie.eversity.exceptions.UserNotRegistered
import by.enrollie.eversity.security.User
import by.enrollie.eversity.tokenCacheValidityMinutes
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.Minutes
import java.util.*

/**
 * Temporary cache of valid access tokens.
 */
var validTokensList = mutableListOf<Triple<Int, String, DateTime>>()

/**
 * Temporary cache for users (their types and ID's)
 */
private var usersCacheList = mutableListOf<Pair<User, DateTime>>()

/**
 * Temporary cache for pupils data
 */
private var pupilsCacheList = mutableListOf<Pair<Pupil, DateTime>>()

/**
 * Temporary cache for user's credentials
 */
private var credentialsCacheList = mutableListOf<Triple<Int, Pair<String?, String?>, String>>()


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
    fun registerTeacher(
        userID: Int,
        fullName: Triple<String, String, String>,
        cookies: Pair<String, String>,
        tokenAPI: String,
        classID: Int? = null
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
        if (!validateDaysMap(daysMap)) {
            throw IllegalArgumentException("daysMap is not valid.")
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
    fun invalidateAllTokens(userID: Int, reason: String?): Int {
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
                it.first == userID && it.second == token && Minutes.minutesBetween(
                    it.third,
                    DateTime.now()
                ).minutes <= tokenCacheValidityMinutes
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
        return Pair(false, null)
        //TODO: Add getBanReason() or something like that
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
        val cachedCredentials = credentialsCacheList.find {
            it.first == userID
        }
        if (cachedCredentials != null) {
            return Triple(cachedCredentials.second.first, cachedCredentials.second.second, cachedCredentials.third)
        }
        val credentialsList = transaction {
            Credentials.select {
                Credentials.id eq userID
            }.toList()
        }
        if (credentialsList.isEmpty()) {
            throw NoSuchElementException("Credentials list for user ID $userID is empty")
        }
        val credentials =
            credentialsList.firstOrNull() ?: throw NoSuchElementException("Credentials first element is null")
        credentialsCacheList.add(
            Triple(
                userID, Pair(
                    credentials[Credentials.csrfToken],
                    credentials[Credentials.sessionID]
                ), credentials[Credentials.token]
            )
        )
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
        credentialsCacheList.removeIf {
            it.first == userID
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
        credentialsCacheList.add(Triple(userID, Pair(credentials.first, credentials.second), credentials.third))
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
        if (!validateDaysMap(daysMap)) {
            throw IllegalArgumentException("daysMap is not valid.")
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

    /**
     * Returns user's type
     * @param userID User's ID
     */
    fun getUserType(userID: Int): APIUserType {
        val cachedUser = usersCacheList.find {
            it.first.id == userID && Minutes.minutesBetween(
                it.second,
                DateTime.now()
            ).minutes <= tokenCacheValidityMinutes
        }
        if (cachedUser != null)
            return cachedUser.first.type
        val type = transaction {
            Users.select {
                Users.id eq userID
            }.toList().firstOrNull()
        } ?: throw UserNotRegistered("User with ID $userID not found in database.")
        return APIUserType.valueOf(type[Users.type])
    }

    /**
     * Returns pupil's class ID
     *
     * @param userID Pupil's ID
     * @return Class ID
     * @throws IllegalArgumentException Thrown, if user is not registered OR is not found in "Pupils" table
     */
    fun getPupilClass(userID: Int): Int {
        if (!doesUserExist(userID)) {
            throw IllegalArgumentException("User with ID $userID not found in database.")
        }
        val classIDElement = transaction {
            Pupils.select {
                Pupils.id eq userID
            }.toList().firstOrNull()
        } ?: throw IllegalArgumentException("Pupil with ID $userID not found in Pupils table.")
        return classIDElement[Pupils.classID]
    }

    /**
     * Validates given dayMap to contain full timetable
     * @param daysMap Map of days
     * @return True, if timetable is valid. False otherwise
     */
    private fun validateDaysMap(daysMap: Map<DayOfWeek, Any>): Boolean {
        if (daysMap.size !in 6..7)
            return false
        for (i in 0 until 6) {
            if (!daysMap.containsKey(DayOfWeek.values()[i]))
                return false
        }
        return true
    }

    /**
     * Invalidates given user token
     * @param userID User ID
     * @param token Token to invalidate
     * @param reason Reason of invalidation (if null, defaults to "Unknown")
     * @return True, if token was banned. False, if user is not found.
     */
    fun invalidateSingleToken(userID: Int, token: String, reason: String?):Boolean {
        if (!doesUserExist(userID)) {
            return false
        }
        validTokensList.removeIf {
            it.first == userID && it.second == token
        }
        transaction {
            Tokens.deleteWhere {
                Tokens.userID eq userID
                Tokens.token eq token
            }
            BannedTokens.insert {
                it[this.userID] = userID
                it[this.token] = token
                it[this.reason] = reason ?: "Unknown"
                it[this.banDate] = DateTime.now()
            }
        }
        return true
    }
}