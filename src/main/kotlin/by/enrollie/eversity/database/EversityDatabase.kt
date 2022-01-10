/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

@file:Suppress("DuplicatedCode", "DuplicatedCode", "DuplicatedCode", "DuplicatedCode")

package by.enrollie.eversity.database

import by.enrollie.eversity.data_classes.*
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.joda.time.DateTime
import java.time.Duration

val tokensCache: Cache<Pair<UserID, String>, Boolean> =
    Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(10)).maximumSize(10000).recordStats().build()
val usersCache: Cache<UserID, User> =
    Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(10)).maximumSize(10000).recordStats().build()
val classesCache: Cache<ClassID, SchoolClass> =
    Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(5)).maximumSize(100).recordStats().build()
val classesAbsenceCache: Cache<Pair<ClassID, DateTime>, Set<Absence>> =
    Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(2)).maximumSize(10000).recordStats().build()
val classTimetablesCache: Cache<ClassID, Timetable> =
    Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(5)).maximumSize(100).recordStats().build()
val teacherTimetableCache: Cache<UserID, TwoShiftsTimetable> =
    Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(10)).maximumSize(100).recordStats().build()
