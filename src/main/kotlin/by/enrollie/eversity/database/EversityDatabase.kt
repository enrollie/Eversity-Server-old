package by.enrollie.eversity.database

import by.enrollie.eversity.data_classes.*
import by.enrollie.eversity.database.tables.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

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
        val result = transaction {
            val query: Query = Users.select { Users.id eq userID }
            query.toList()
        }
        return result.isEmpty()
    }

    /**
     * Registers class teacher (does not check for credentials validity) and class itself.
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
        transaction {
            Users.insert {
                it[id] = userID
                it[type] = APIUserType.Teacher.name
            }
            Teachers.insert {
                it[id] = userID
                it[firstName] = fullName.first
                it[middleName] = fullName.second
                it[lastName] = fullName.third
                it[this.classID] = classID
            }
            Credentials.insert {
                it[id] = userID
                it[csrfToken] = cookies.first
                it[sessionID] = cookies.second
                it[token] = tokenAPI
            }
            Classes.insert {
                it[classTeacher] = userID
                it[this.classID] = classID
            }
        }
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
     */
    fun registerPupil(userID: Int, name: Pair<String, String>, classID: Int): Boolean {
        if (doesUserExist(userID)) {
            return false
        }
        transaction {
            Users.insert {
                it[id] = userID
                it[type] = APIUserType.Pupil.name
            }
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
     * @return Nothing
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
     * @param daysArray Array of [TimetableDay]s. MUST contain at least six elements (days) AND less than 8 elements
     *
     * @throws IllegalArgumentException Thrown, if [daysArray] does not contain some day of week (except SUNDAY) OR it has less than six elements
     */
    fun registerClassTimetable(classID: Int, daysArray: Array<TimetableDay>) {
        if (daysArray.size < 6 || daysArray.size > 7)
            throw IllegalArgumentException("daysArray size is less than six OR more than 7")
        val map = mutableMapOf<DayOfWeek, Array<Lesson>>()
        daysArray.forEach {
            map[it.dayOfWeek] = it.lessonsArray
        }
        for (i in 0 until 6) {
            when (i) {
                DayOfWeek.MONDAY.ordinal -> {
                    if (!map.containsKey(DayOfWeek.MONDAY))
                        throw IllegalArgumentException("daysArray does not contain Monday")
                }
                DayOfWeek.TUESDAY.ordinal -> {
                    if (!map.containsKey(DayOfWeek.TUESDAY))
                        throw IllegalArgumentException("daysArray does not contain Tuesday")
                }
                DayOfWeek.WEDNESDAY.ordinal -> {
                    if (!map.containsKey(DayOfWeek.WEDNESDAY))
                        throw IllegalArgumentException("daysArray does not contain Wednesday")
                }
                DayOfWeek.THURSDAY.ordinal -> {
                    if (!map.containsKey(DayOfWeek.THURSDAY))
                        throw IllegalArgumentException("daysArray does not contain Thursday")
                }
                DayOfWeek.FRIDAY.ordinal -> {
                    if (!map.containsKey(DayOfWeek.FRIDAY))
                        throw IllegalArgumentException("daysArray does not contain Friday")
                }
                DayOfWeek.SATURDAY.ordinal -> {
                    if (!map.containsKey(DayOfWeek.SATURDAY))
                        throw IllegalArgumentException("daysArray does not contain Saturday")
                }
            }
        }
        transaction {
            ClassTimetables.insert {
                it[id] = classID
                it[monday] = Json.encodeToString(map[DayOfWeek.MONDAY])
                it[tuesday] = Json.encodeToString(map[DayOfWeek.TUESDAY])
                it[wednesday] = Json.encodeToString(map[DayOfWeek.WEDNESDAY])
                it[thursday] = Json.encodeToString(map[DayOfWeek.THURSDAY])
                it[friday] = Json.encodeToString(map[DayOfWeek.FRIDAY])
                it[saturday] = Json.encodeToString(map[DayOfWeek.SATURDAY])
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
        if (doesUserExist(userID))
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
                    it[BannedTokens.reason] = reason ?: "Unknown"
                }
            }
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
        val foundInValid = transaction {
            Tokens.select {
                Tokens.userID eq userID
                Tokens.token eq token
            }.toList().isNotEmpty()
        }
        if (foundInValid){
            return Pair(true, null)
        }
        val foundBanned = transaction {
            BannedTokens.select {
                BannedTokens.token eq token
            }.toList()
        }
        if (foundBanned.isEmpty()){
            return Pair(false, null)
        }
        val banReason = foundBanned.firstOrNull() ?: return Pair(false, null)
        //TODO: Add logging
        return Pair(false, banReason.getOrNull(BannedTokens.reason))
    }
}