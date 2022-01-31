/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

@file:Suppress("UNUSED") // Everything is referenced in Oso policy

package by.enrollie.eversity.uac

import by.enrollie.eversity.DATABASE
import by.enrollie.eversity.data_classes.ClassID
import by.enrollie.eversity.data_classes.UserID
import by.enrollie.eversity.data_classes.UserType
import by.enrollie.eversity.data_functions.join
import by.enrollie.eversity.database.functions.getPupilClass
import by.enrollie.eversity.database.functions.getTeacherClass
import by.enrollie.eversity.database.functions.getTeacherTimetable
import by.enrollie.eversity.database.xodus_definitions.XodusUser
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import kotlinx.dnq.query.eq
import kotlinx.dnq.query.firstOrNull
import kotlinx.dnq.query.query
import kotlinx.dnq.query.toList
import org.joda.time.DateTime
import org.joda.time.Interval
import org.joda.time.Seconds
import java.time.DayOfWeek
import java.util.concurrent.TimeUnit


private val roleCache: Cache<UserID, List<OsoSchoolClassRole>> =
    Caffeine.newBuilder().expireAfter(object : Expiry<UserID, List<OsoSchoolClassRole>> {
        override fun expireAfterCreate(key: UserID, value: List<OsoSchoolClassRole>, currentTime: Long): Long =
            TimeUnit.SECONDS.toNanos(value.firstNotNullOfOrNull { it.expireInSeconds }
                ?: TimeUnit.HOURS.toSeconds(1L))

        override fun expireAfterUpdate(
            key: UserID,
            value: List<OsoSchoolClassRole>,
            currentTime: Long,
            currentDuration: Long,
        ): Long = TimeUnit.SECONDS.toNanos(value.firstNotNullOfOrNull { it.expireInSeconds }
            ?: TimeUnit.HOURS.toSeconds(1L))

        override fun expireAfterRead(
            key: UserID?,
            value: List<OsoSchoolClassRole>?,
            currentTime: Long,
            currentDuration: Long,
        ): Long = currentDuration

    }).build()

class OsoUser(val id: UserID, private val userType: UserType) {
    fun type() = userType.name.lowercase()
    fun id() = id
    fun classesRoles(): List<OsoSchoolClassRole> = roleCache.get(id) {
        val classesRoles = DATABASE.transactional(readonly = true) {
            XodusUser.query(XodusUser::id eq id).firstOrNull()?.persistentRoles?.toList()?.map {
                OsoSchoolClassRole(SchoolClassRoles.valueOf(it.role.title.uppercase()),
                    OsoSchoolClass(it.schoolClass.id),
                    null)
            }
        }.let {
            if (userType == UserType.Pupil)
                (it ?: listOf()).plus(OsoSchoolClassRole(SchoolClassRoles.PUPIL,
                    OsoSchoolClass(getPupilClass(id)), null))
            else it ?: listOf()
        }.toMutableList()
        if (userType == UserType.Teacher || userType == UserType.Administration) {
            val schoolClass = getTeacherClass(id)
            val lessonsList = getTeacherTimetable(id)[DayOfWeek.of(DateTime.now().dayOfWeek)].join()
            val lesson = run {
                val currTime = DateTime.now()
                lessonsList.find {
                    Interval(
                        DateTime.now()
                            .withTime(it.schedule.startHour.toInt(), it.schedule.startMinute.toInt(), 0, 0),
                        DateTime.now().withTime(it.schedule.endHour.toInt(), it.schedule.endMinute.toInt(), 0, 0)
                    ).contains(currTime)
                }
            }
            if (schoolClass != null)
                classesRoles.add(OsoSchoolClassRole(SchoolClassRoles.CLASS_TEACHER,
                    OsoSchoolClass(schoolClass.id),
                    null))
            if (lesson != null) {
                classesRoles.add(OsoSchoolClassRole(SchoolClassRoles.LESSON_TEACHER,
                    OsoSchoolClass(lesson.classID),
                    Seconds.secondsBetween(DateTime.now(), DateTime.now()
                        .withTime(lesson.schedule.endHour.toInt(),
                            lesson.schedule.endMinute.toInt(),
                            0,
                            0)).seconds.toLong()
                ))
            }
        }
        classesRoles
    }

    fun anyMutualClasses(second: List<OsoSchoolClassRole>): Boolean = second.let { it.map { it.schoolClass.id } }
        .let { idList -> classesRoles().map { it.schoolClass.id }.any { idList.contains(it) } }

}

enum class SchoolClassRoles { CLASS_TEACHER, DATA_DELEGATE, LESSON_TEACHER, PUPIL }

class OsoSchoolClass(val id: ClassID) {
    fun id() = id
}

class OsoSchoolClassRole(
    private val role: SchoolClassRoles,
    val schoolClass: OsoSchoolClass,
    val expireInSeconds: Long?,
) {
    fun name() = role.name.lowercase()
    fun schoolClass() = schoolClass
}

class School
