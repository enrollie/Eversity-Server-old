/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database

import by.enrollie.eversity.data_classes.*


/**
 * Temporary cache for users (their types and ID's)
 */
private val usersCacheSet = mutableSetOf<User>()

/**
 * Temporary cache for pupils data
 */
private val pupilsCacheSet = mutableSetOf<Pupil>()

/**
 * Temporary cache for teachers data.
 * First element is a teacher and second element is their class ID (if teacher is a class teacher) or null
 */
private val teachersCacheSet = mutableSetOf<Pair<Teacher, Int?>>()

/**
 * Temporary cache for user's credentials
 */
private val credentialsCacheSet = mutableSetOf<Triple<Int, Pair<String?, String?>, String>>()

private val classTimetableCacheSet = mutableSetOf<Pair<Int, Map<DayOfWeek, Array<Lesson>>>>()

private val classesCacheSet = mutableSetOf<SchoolClass>()

/**
 * Finds cached user by [userID]
 */
fun findCachedUser(userID: Int): User? = usersCacheSet.find { it.id == userID }

/**
 * Finds cached pupil by [pupilID]
 */
fun findCachedPupil(pupilID: Int): Pupil? = pupilsCacheSet.find { it.id == pupilID }

/**
 * Finds cached teacher by [teacherID]
 */
fun findCachedTeacher(teacherID: Int): Pair<Teacher, Int?>? =
    teachersCacheSet.find { it.first.id == teacherID }

/**
 * Finds cached credentials by [userID]
 */
fun findCachedCredentials(userID: Int): Pair<Pair<String?, String?>, String>? {
    val foundData = credentialsCacheSet.find { it.first == userID } ?: return null
    return Pair(foundData.second, foundData.third)
}

/**
 * Finds cached class timetable by [classID]
 */
fun findCachedClassTimetable(classID: Int): Map<DayOfWeek, Array<Lesson>>? =
    classTimetableCacheSet.find { it.first == classID }?.second

fun findCachedClass(classID: Int) = classesCacheSet.find { it.id == classID }

/**
 * Adds [user] to cache
 */
fun cacheUser(user: User) = usersCacheSet.add(user)

/**
 * Adds [pupil] to cache
 */
fun cachePupil(pupil: Pupil) {
    pupilsCacheSet.add(pupil); usersCacheSet.add(User(pupil.id, APIUserType.Pupil))
}

/**
 * Adds class teacher to cache
 */
fun cacheTeacher(teacher: Teacher, classID: Int?) {
    teachersCacheSet.add(Pair(teacher, classID)); usersCacheSet.add(
        User(teacher.id, APIUserType.Teacher)
    )
}

/**
 * Adds [credentials] to cache and replaces (if any) already cached ones
 */
fun cacheCredentials(userID: Int, credentials: Pair<Pair<String?, String?>, String>) {
    invalidateCredentialsCache(userID)
    credentialsCacheSet.add(Triple(userID, credentials.first, credentials.second))
}

/**
 * Caches classes timetable
 * @throws IllegalArgumentException Thrown, if [timetable] is not valid
 */
fun cacheClassTimetable(classID: Int, timetable: Map<DayOfWeek, Array<Lesson>>) {
    require(validateDaysMap(timetable)) { "Given timetable is not valid" }
    classTimetableCacheSet.add(Pair(classID, timetable))
}

/**
 * Caches school class
 */
fun cacheClass(schoolClass: SchoolClass){
    classesCacheSet.add(schoolClass)
    pupilsCacheSet.addAll(schoolClass.pupils)
    usersCacheSet.addAll(schoolClass.pupils.map { User(it.id, APIUserType.Pupil) })
}

/**
 * Invalidates credentials of user with [userID]
 */
fun invalidateCredentialsCache(userID: Int) = credentialsCacheSet.removeIf { it.first == userID }
