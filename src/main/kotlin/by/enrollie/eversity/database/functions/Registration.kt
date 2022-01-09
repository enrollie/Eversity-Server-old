/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.DATABASE
import by.enrollie.eversity.data_classes.Pupil
import by.enrollie.eversity.data_classes.Timetable
import by.enrollie.eversity.data_classes.TwoShiftsTimetable
import by.enrollie.eversity.data_classes.UserName
import by.enrollie.eversity.database.xodus_definitions.*
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.creator.findOrNew
import kotlinx.dnq.query.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Registers school class without registering anything else
 *
 * @param classID ID of class
 * @param classTeacherID ID of class teacher (does not need to be registered)
 */
fun registerClass(
    classID: Int,
    classTeacherID: Int,
    title: String,
    isSecondShift: Boolean,
    timetable: Timetable,
    store: TransientEntityStore = DATABASE
) {
    store.transactional {
        val newClass = XodusClass.findOrNew(XodusClass.query(XodusClass::id eq classID)) {
            id = classID
            classTitle = title
            this.isSecondShift = isSecondShift
            classTeacher =
                XodusTeacherProfile.query(XodusTeacherProfile::user.matches(XodusUser::id eq classTeacherID)).first()
            this.timetable.delete()
            this.timetable = XodusClassTimetable.new {
                schoolClass = this@findOrNew
                this.monday = Json.encodeToString(timetable.monday)
                this.tuesday = Json.encodeToString(timetable.tuesday)
                this.wednesday = Json.encodeToString(timetable.wednesday)
                this.thursday = Json.encodeToString(timetable.thursday)
                this.friday = Json.encodeToString(timetable.friday)
                this.saturday = Json.encodeToString(timetable.saturday)
            }
        }
        XodusTeacherProfile.query(XodusTeacherProfile::user.matches(XodusUser::id eq classTeacherID))
            .first().schoolClass = newClass
    }
    return
}

/**
 * Registers class teacher (does not check for credentials validity).
 * (does not register pupils, neither timetables)
 *
 * @param userID User ID of class teacher
 * @param fullName [Triple] First - First name, Second - Middle name, Third - Last name
 * @param cookies [Pair] First - csrftoken, second - sessionID
 * @param isAdministration Defines, should user be created with administration rights or not
 *
 * @return False, if user already exists. True, if operation succeed
 */
fun registerTeacher(
    userID: Int,
    name: UserName,
    cookies: Pair<String, String>?,
    timetable: TwoShiftsTimetable,
    isAdministration: Boolean = false,
    store: TransientEntityStore = DATABASE
): Boolean {
    store.transactional {
        XodusUser.findOrNew(findQuery = XodusUser.query(XodusUser::id eq userID)) {
            id = userID
            type = if (isAdministration) XodusUserType.ADMINISTRATION else XodusUserType.TEACHER
            firstName = name.firstName
            middleName = name.middleName
            lastName = name.lastName
            if (isAdministration) {
                XodusAdministrationProfile.new {
                    user = this@findOrNew
                    this.timetable = Json.encodeToString(timetable)
                }
            } else {
                XodusTeacherProfile.new {
                    user = this@findOrNew
                    this.timetable = Json.encodeToString(timetable)
                }
            }
            if (cookies != null) {
                XodusSchoolsBy.new {
                    user = this@findOrNew
                    csrfToken = cookies.first
                    sessionID = cookies.second
                }
            }
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
 * @throws NoSuchElementException Thrown, if pupil's class is not yet registered.
 */
fun registerPupil(
    userID: Int,
    name: UserName,
    classID: Int,
    store: TransientEntityStore = DATABASE
) {
    store.transactional {
        XodusUser.findOrNew(XodusUser.query(XodusUser::id eq userID)) {
            id = userID
            type = XodusUserType.PUPIL
            firstName = name.firstName
            middleName = name.middleName
            lastName = name.lastName
            // Users normally don't have more profiles than one, but to be sure we'll delete everything
            profile.toList().forEach {
                delete()
            }
            XodusPupilProfile.new {
                user = this@findOrNew
                schoolClass = XodusClass.query(XodusClass::id eq classID).first()
            }
        }
    }
}

/**
 * Registers many pupils at once. (does not register their timetables).
 * Ignores pupil if it already exists.
 * @param array Array, containing [Pupil]s to register
 * @throws NoSuchElementException Thrown, when pupil class does not exist
 */
fun registerManyPupils(array: Array<Pupil>, store: TransientEntityStore = DATABASE) {
    val list = array.toList()
    store.transactional {
        list.forEach { pupil ->
            XodusUser.findOrNew(XodusUser.query(XodusUser::id eq pupil.id)) {
                id = pupil.id
                type = XodusUserType.PUPIL
                firstName = pupil.firstName
                middleName = pupil.middleName
                lastName = pupil.lastName
                // Users normally don't have more profiles than one, but to be sure we'll delete everything
                profile.toList().forEach {
                    delete()
                }
                XodusPupilProfile.new {
                    user = this@findOrNew
                    schoolClass =
                        XodusClass.query(XodusClass::id eq pupil.classID).firstOrNull() ?: throw NoSuchElementException(
                            "Class with ID ${pupil.classID} does not exist"
                        )
                }
            }
        }
    }
}

fun registerParent(
    userID: Int,
    name: UserName,
    cookies: Pair<String, String>?,
    store: TransientEntityStore = DATABASE
) {
    store.transactional {
        XodusUser.findOrNew(XodusUser.query(XodusUser::id eq userID)) {
            id = userID
            type = XodusUserType.PARENT
            firstName = name.firstName
            middleName = name.middleName
            lastName = name.lastName
            XodusParentProfile.new {
                this.pupils
            }
            cookies?.let { cookies->
                XodusSchoolsBy.new {
                    user = this@findOrNew
                    csrfToken = cookies.first
                    sessionID = cookies.second
                }
            }
        }
    }
}
