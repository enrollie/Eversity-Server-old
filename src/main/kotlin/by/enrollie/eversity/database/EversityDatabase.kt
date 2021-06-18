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
import by.enrollie.eversity.exceptions.ClassNotRegistered
import by.enrollie.eversity.exceptions.UserNotRegistered
import by.enrollie.eversity.tokenCacheValidityMinutes
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.Minutes
import java.text.SimpleDateFormat
import java.util.*

/**
 * Temporary cache of valid access tokens.
 */
var validTokensSet = mutableSetOf<Triple<Int, String, DateTime>>()

/**
 * Temporary cache for users (their types and ID's)
 */
private var usersCacheSet = mutableSetOf<Pair<User, DateTime>>()

/**
 * Temporary cache for pupils data
 */
private var pupilsCacheSet = mutableSetOf<Pupil>()

/**
 * Temporary cache for teachers data.
 * First element is a teacher and second element is their class ID (if teacher is a class teacher) or null
 */
private var teachersCacheSet = mutableSetOf<Pair<Teacher, Int?>>()

/**
 * Temporary cache for user's credentials
 */
private var credentialsCacheSet = mutableSetOf<Triple<Int, Pair<String?, String?>, String>>()

/**
 *
 */
private val classesCacheSet = mutableSetOf<SchoolClass>()


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
        if (usersCacheSet.find {
                it.first.id == userID
            } != null) {
            return true
        }
        val tos = transaction {
            Users.select { Users.id eq userID }.toList()
        }
        if (tos.isNotEmpty())
            GlobalScope.launch { usersCacheSet.add(Pair(User(userID, getUserType(userID)), DateTime.now())) }
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
        teachersCacheSet.add(Pair(Teacher(userID, fullName.first, fullName.second, fullName.third), classID))
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
        val list = array.toList()
        transaction {
            Users.batchInsert(list, shouldReturnGeneratedValues = false, ignore = true) { pupil ->
                this[Users.id] = pupil.id
                this[Users.type] = APIUserType.Pupil.name
            }
            Pupils.batchInsert(list, shouldReturnGeneratedValues = false, ignore = true) { pupil ->
                this[Pupils.id] = pupil.id
                this[Pupils.firstName] = pupil.firstName
                this[Pupils.lastName] = pupil.lastName
                this[Pupils.classID] = pupil.classID
            }
        }

    }

    /**
     * Registers (or, overwrites, if exists) timetable for class
     *
     * @param classID ID of pupil
     * @param daysMap Map of <[DayOfWeek], Array of [Lesson]>. MUST contain at least six elements (days) AND less than 8 elements
     *
     * @throws IllegalArgumentException Thrown, if [daysMap] does not contain some day of week (except SUNDAY) OR it has less than six elements
     */
    fun registerClassTimetable(classID: Int, daysMap: Map<DayOfWeek, Array<Lesson>>) {
        require(validateDaysMap(daysMap)) { "Day map should be valid" }
        if (!doesClassExist(classID)) {
            throw IllegalArgumentException("Class with ID $classID does not exist")
        }
        transaction {
            ClassTimetables.deleteWhere {
                ClassTimetables.id eq classID
            }
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
                    it[token] = res[Tokens.token]
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
                Tokens.userID eq userID
                Tokens.token eq token
            }.toList().isNotEmpty()
        }
        if (foundInValid) {
            validTokensSet.add(Triple(userID, token, DateTime.now()))
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
            throw UserNotRegistered("User with ID $userID does not exist")
        }
        val cachedCredentials = credentialsCacheSet.find {
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
        credentialsCacheSet.add(
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
            throw UserNotRegistered("User with ID $userID does not exist")
        }
        credentialsCacheSet.removeIf {
            it.first == userID
        }
        transaction {
            Credentials.deleteWhere {
                Credentials.id eq userID
            }
            Credentials.insert {
                it[id] = userID
                it[csrfToken] = credentials.first
                it[sessionID] = credentials.second
                it[token] = credentials.third
            }
        }
        credentialsCacheSet.add(Triple(userID, Pair(credentials.first, credentials.second), credentials.third))
    }

    /**
     * Registers (or, updates) school class (does not register class teacher, pupils, timetables etc.)
     *
     * @param classID ID of class
     * @param classTeacherID ID of class teacher (does not need to be registered)
     */
    fun registerClass(classID: Int, classTeacherID: Int, name: String, isSecondShift: Boolean) {
        if (doesClassExist(classID))
            return
        transaction {
            Classes.insert {
                it[this.classID] = classID
                it[this.classTeacher] = classTeacherID
                it[this.name] = name
                it[this.isSecondShift] = isSecondShift
            }
        }
        return
    }

    /**
     * Registers (or, overwrites, if exists) timetable for teacher
     *
     * @param teacherID ID of pupil
     * @param daysMap Map of <[DayOfWeek], Array of [TeacherLesson]]]>. MUST contain at least six elements (days) AND less than 8 elements
     *
     * @throws IllegalArgumentException Thrown, if [daysMap] does not contain some day of week (except SUNDAY) OR it has less than six elements
     */
    fun registerTeacherTimetable(teacherID: Int, daysMap: Map<DayOfWeek, Array<TeacherLesson>>) {
        if (!validateDaysMap(daysMap)) {
            throw IllegalArgumentException("daysMap is not valid.")
        }
        transaction {
            TeachersTimetable.deleteWhere {
                TeachersTimetable.id eq teacherID
            }
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
     * Determines, whether class should be updated or not
     * @param classID ID of class
     * @return True, if you SHOULD (!) update class data. False otherwise
     */
    fun shouldUpdateClass(classID: Int): Boolean {
        val classExisting = doesClassExist(classID)
        val (existingPupils, existingTimetable) = transaction {
            Pair(
                Pupils.select {
                    Pupils.classID eq classID
                }.toList().isNotEmpty(),
                ClassTimetables.select {
                    ClassTimetables.id eq classID
                }.toList().isNotEmpty()
            )
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
     * @throws UserNotRegistered Thrown, if user is not registered
     */
    fun getUserType(userID: Int): APIUserType {
        val cachedUser = usersCacheSet.find {
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
            throw UserNotRegistered("User with ID $userID does not exist")
        }
        if (getUserType(userID) != APIUserType.Pupil)
            throw IllegalArgumentException("User with ID $userID is not a pupil (they are ${getUserType(userID)}, in fact)")
        val cachedPupil = pupilsCacheSet.find {
            it.id == userID
        }
        if (cachedPupil != null)
            return cachedPupil.classID
        val classIDElement = transaction {
            Pupils.select {
                Pupils.id eq userID
            }.toList().firstOrNull()
        } ?: throw IllegalArgumentException("Pupil with ID $userID not found in Pupils table.")
        GlobalScope.launch { getUserName(userID, APIUserType.Pupil) } //This call will asynchronously cache pupil
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
    fun invalidateSingleToken(userID: Int, token: String, reason: String?): Boolean {
        if (!doesUserExist(userID)) {
            return false
        }
        validTokensSet.removeIf {
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

    /**
     * Returns user's full name
     * @param userID User ID
     * @param type User type (if you don't have one, see [getUserType]
     * @return Triple, made of First name, Middle name and Last name correspondingly
     */
    fun getUserName(userID: Int, type: APIUserType): Triple<String, String?, String> {
        if (!doesUserExist(userID)) {
            throw UserNotRegistered("User with ID $userID does not exist")
        }
        return when (type) {
            APIUserType.Pupil -> {
                val cachedPupil = pupilsCacheSet.find {
                    it.id == userID
                }
                if (cachedPupil != null)
                    return Triple(cachedPupil.firstName, null, cachedPupil.lastName)
                val pupil = transaction {
                    Pupils.select {
                        Pupils.id eq userID
                    }.toList().firstOrNull()
                }
                    ?: throw NoSuchElementException("Pupil with ID $userID was not found in Pupils database. Are you sure that user with ID $userID is a pupil?")
                pupilsCacheSet.add(
                    Pupil(
                        userID,
                        pupil[Pupils.firstName],
                        pupil[Pupils.lastName],
                        pupil[Pupils.classID]
                    )
                )
                Triple(pupil[Pupils.firstName], null, pupil[Pupils.lastName])
            }
            APIUserType.Parent -> {
                Triple("WE DON\'T STORE PARENTS DATA YET", null, "why did you even ask lol")
            }
            APIUserType.Teacher -> {
                //TODO: Add caching
                val teacher = transaction {
                    Teachers.select {
                        Teachers.id eq userID
                    }.toList().firstOrNull()
                }
                    ?: throw NoSuchElementException("Teacher with ID $userID was not found in database. Are you sure that user $userID is a teacher?")
                Triple(teacher[Teachers.firstName], teacher[Teachers.middleName], teacher[Teachers.lastName])
            }
            APIUserType.SYSTEM -> {
                //TODO: Record tech guys info somewhere
                Triple("Eversity", "System", "Administrator")
            }
        }
    }

    /**
     * Returns teacher's timetable
     * @param userID Teacher's ID
     * @return Map, containing six arrays of lessons (may be unsorted), mapped to six [DayOfWeek]'s (from Monday to Saturday)
     */
    fun getTeacherTimetable(userID: Int): Map<DayOfWeek, Array<TeacherLesson>> {
        if (!doesUserExist(userID)) {
            throw UserNotRegistered("User with ID $userID does not exist")
        }
        if (getUserType(userID) != APIUserType.Teacher)
            throw IllegalArgumentException("User with ID $userID is not a Teacher (they are ${getUserType(userID).name}, in fact)")
        //TODO: Add caching
        val timetable = transaction {
            TeachersTimetable.select {
                TeachersTimetable.id eq userID
            }.toList().firstOrNull()
        }
            ?: throw NoSuchElementException("Timetable of teacher with ID $userID was not found. Most likely, database bad times are coming")
        val timetableMap = mutableMapOf<DayOfWeek, Array<TeacherLesson>>()
        //Danger: big chunk of code incoming
        timetableMap[DayOfWeek.MONDAY] =
            Json.decodeFromString(timetable[TeachersTimetable.monday])
        timetableMap[DayOfWeek.TUESDAY] =
            Json.decodeFromString(timetable[TeachersTimetable.tuesday])
        timetableMap[DayOfWeek.WEDNESDAY] =
            Json.decodeFromString(timetable[TeachersTimetable.wednesday])
        timetableMap[DayOfWeek.THURSDAY] =
            Json.decodeFromString(timetable[TeachersTimetable.thursday])
        timetableMap[DayOfWeek.FRIDAY] =
            Json.decodeFromString(timetable[TeachersTimetable.friday])
        timetableMap[DayOfWeek.SATURDAY] =
            Json.decodeFromString(timetable[TeachersTimetable.saturday])
        return timetableMap
    }

    /**
     * Returns class timetable
     * @param classID ID of class
     * @return Map, containing six arrays of lessons (may be unsorted), mapped to six [DayOfWeek]'s (from Monday to Saturday)
     */
    fun getClassTimetable(classID: Int): Map<DayOfWeek, Array<Lesson>> {
        if (!doesClassExist(classID)) {
            throw ClassNotRegistered("Class with ID $classID does not exist")
        }
        val timetableQuery = transaction {
            ClassTimetables.select {
                ClassTimetables.id eq classID
            }.toList().firstOrNull()
        }
            ?: throw NoSuchElementException("Class timetable of class with id $classID was not found. Database is waiting for bad times")
        val timetableMap = mutableMapOf<DayOfWeek, Array<Lesson>>()
        //Danger: big chunk of code incoming
        timetableMap[DayOfWeek.MONDAY] =
            Json.decodeFromString(timetableQuery[ClassTimetables.monday])
        timetableMap[DayOfWeek.TUESDAY] =
            Json.decodeFromString(timetableQuery[ClassTimetables.tuesday])
        timetableMap[DayOfWeek.WEDNESDAY] =
            Json.decodeFromString(timetableQuery[ClassTimetables.wednesday])
        timetableMap[DayOfWeek.THURSDAY] =
            Json.decodeFromString(timetableQuery[ClassTimetables.thursday])
        timetableMap[DayOfWeek.FRIDAY] =
            Json.decodeFromString(timetableQuery[ClassTimetables.friday])
        timetableMap[DayOfWeek.SATURDAY] =
            Json.decodeFromString(timetableQuery[ClassTimetables.saturday])
        return timetableMap
    }

    /**
     * Retrieves pupil's timetable
     * @param userID ID of pupil
     * @throws IllegalArgumentException Thrown, if pupil is not found in database
     */
    fun getPupilTimetable(userID: Int) = getClassTimetable(getPupilClass(userID))

    fun insertMark(mark: Mark, journalID: Int, lessonID: Int) {
        require(doesUserExist(mark.pupil.id)) { "Pupil with ID ${mark.pupil.id} does not exist" }
        require(doesClassExist(mark.pupil.classID)) { "Class with ID ${mark.pupil.classID} does not exist" }
        transaction {
            Marks.deleteWhere {
                Marks.id eq mark.id
            }
            Marks.insert {
                it[id] = mark.id
                it[value] = mark.markNum
                it[pupilID] = mark.pupil.id
                it[classID] = mark.pupil.classID
                it[date] = SimpleDateFormat("YYYY-MM-dd").format(Calendar.getInstance().time)
                it[this.journalID] = journalID
                it[this.lessonID] = lessonID
            }
        }
    }

    fun batchInsertMarks(marksList: List<Pair<Mark, Pair<Int, Int>>>) {
        marksList.forEach {
            require(doesUserExist(it.first.pupil.id)) { "Pupil with ID ${it.first.pupil.id} does not exist" }
            require(doesClassExist(it.first.pupil.classID)) { "Class with ID ${it.first.pupil.classID} does not exist" }
        }
        transaction {
            Marks.batchInsert(marksList, ignore = true, shouldReturnGeneratedValues = false) {
                val mark = it.first
                this[Marks.id] = mark.id
                this[Marks.value] = mark.markNum
                this[Marks.pupilID] = mark.pupil.id
                this[Marks.classID] = mark.pupil.classID
                this[Marks.date] = SimpleDateFormat("YYYY-MM-dd").format(Calendar.getInstance().time)
                this[Marks.journalID] = it.second.first
                this[Marks.lessonID] = it.second.second
            }
        }
    }

    fun insertAbsence(pupil: Pupil, reason: AbsenceReason, date: DateTime) {
        require(doesUserExist(pupil.id)) { "Pupil with ID ${pupil.id} does not exist" }
        require(doesClassExist(pupil.classID)) { "Class with ID ${pupil.classID} does not exist" }
        transaction {
            Absences.deleteWhere {
                Absences.pupilID eq pupil.id
                Absences.date eq date
            }
            Absences.insertIgnore {
                it[pupilID] = pupil.id
                it[classID] = pupil.classID
                it[this.date] = date
                it[this.reason] = reason.name
            }
        }
    }

    /**
     * Removes absence from database
     * @param pupil Pupil to remove absence for
     * @param date to remove absence
     */
    fun removeAbsence(pupil: Pupil, date: DateTime) {
        transaction {
            Absences.deleteWhere {
                Absences.pupilID eq pupil.id
                Absences.date eq date
            }
        }
    }

    fun getTeacher(teacherID: Int): Pair<Teacher, Int?> {
        require(doesUserExist(teacherID)) { "User with ID $teacherID does not exist" }

        val teacherData = transaction {
            Teachers.select {
                Teachers.id eq teacherID
            }.firstOrNull()
        } ?: throw IllegalArgumentException("Teacher with ID $teacherID does not exist")
        val teacher = Teacher(
            teacherID,
            teacherData[Teachers.firstName],
            teacherData[Teachers.middleName],
            teacherData[Teachers.lastName]
        )
        val classID = teacherData[Teachers.classID]
        return Pair(teacher, classID)
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

    fun getClass(classID: Int): SchoolClass {
        require(doesClassExist(classID)) { "Class with ID $classID does not exist." }
        val cachedClass = classesCacheSet.find { it.id == classID }
        if (cachedClass != null)
            return cachedClass
        val (classData, pupilsData) = transaction {
            return@transaction Pair(
                Classes.select {
                    Classes.classID eq classID
                }.toList().firstOrNull(),
                Pupils.select {
                    Pupils.classID eq classID
                }.toList()
            )
        }
        if (classData == null)
            throw IllegalStateException("Class data is null")
        val pupilsList = mutableListOf<Pupil>()
        pupilsData.forEach {
            pupilsList.add(
                Pupil(
                    it[Pupils.id],
                    it[Pupils.firstName],
                    it[Pupils.lastName],
                    it[Pupils.classID]
                )
            )
        }
        pupilsCacheSet.addAll(pupilsList)
        return SchoolClass(
            classData[Classes.classID],
            classData[Classes.name],
            classData[Classes.classTeacher],
            pupilsList.toTypedArray()
        )
    }

    fun getClassPupils(classID: Int): List<Pupil> {
        require(doesClassExist(classID)) { "Class with ID $classID does not exist" }
        return transaction {
            Pupils.select { Pupils.classID eq classID }.toList().map {
                Pupil(it[Pupils.id], it[Pupils.firstName], it[Pupils.lastName], it[Pupils.classID])
            }
        }
    }

    fun getClassAbsence(classID: Int, startDate: String, endDate: String): Set<Absence> {
        require(doesClassExist(classID)) { "Class with ID $classID does not exist" }
        val startDateTime = DateTime.parse(startDate)
        val endDateTime = DateTime.parse(endDate)
        return transaction {
            Absences.select {
                Absences.classID eq classID
                Absences.date greaterEq startDateTime
            }.toList().filter{
                it[Absences.date] <= endDateTime
            }.map {
                Absence(
                    it[Absences.pupilID],
                    it[Absences.classID],
                    it[Absences.date].toString("YYYY-MM-dd"),
                    try {
                        AbsenceReason.valueOf(it[Absences.reason])
                    } catch (e: IllegalArgumentException) {
                        AbsenceReason.UNKNOWN
                    }
                )
            }.toSet()
        }
    }

    fun getClassAbsence(classID: Int, day: String): Set<Absence> {
        require(doesClassExist(classID)) { "Class with ID $classID does not exist" }
        val requiredDateTime = DateTime.parse(day)
        return transaction {
            Absences.select {
                Absences.classID eq classID
                Absences.date eq requiredDateTime
            }.toList().map {
                println(it[Absences.date])
                Absence(
                    it[Absences.pupilID],
                    it[Absences.classID],
                    it[Absences.date].toString("YYYY-MM-dd"),
                    try {
                        AbsenceReason.valueOf(it[Absences.reason])
                    } catch (e: IllegalArgumentException) {
                        AbsenceReason.UNKNOWN
                    }
                )
            }.toSet()
        }
    }

    fun getClassAbsence(classID: Int): Set<Absence> {
        require(doesClassExist(classID)) { "Class with ID $classID does not exist" }
        return transaction {
            Absences.select {
                Absences.classID eq classID
                Absences.date eq DateTime.now()
            }.toList().map {
                Absence(
                    it[Absences.pupilID],
                    it[Absences.classID],
                    it[Absences.date].toString("YYYY-MM-dd"),
                    try {
                        AbsenceReason.valueOf(it[Absences.reason])
                    } catch (e: IllegalArgumentException) {
                        AbsenceReason.UNKNOWN
                    }
                )
            }.toSet()
        }
    }
}