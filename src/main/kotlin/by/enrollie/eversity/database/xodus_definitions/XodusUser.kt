/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.xodus_definitions

import by.enrollie.eversity.DATABASE
import by.enrollie.eversity.data_classes.*
import by.enrollie.eversity.security.User
import jetbrains.exodus.database.TransientEntityStore
import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.*
import kotlinx.dnq.query.eq
import kotlinx.dnq.query.firstOrNull
import kotlinx.dnq.query.query
import kotlinx.dnq.simple.min
import kotlinx.dnq.simple.requireIf

class XodusUser(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XodusUser>()

    var id by xdRequiredIntProp(unique = true) { min(0) }
    var firstName by xdRequiredStringProp { }
    var middleName by xdStringProp {
        requireIf {
            when (type) {
                XodusUserType.ADMINISTRATION, XodusUserType.SOCIAL_TEACHER, XodusUserType.TEACHER -> true
                else -> false
            }
        }
    }
    var lastName by xdRequiredStringProp { }
    var type by xdLink1(XodusUserType)
    val profile by xdChildren0_N(XodusBaseUserProfile::user)
    val accessTokens by xdChildren0_N(XodusToken::user)
    val schoolsByCredentials by xdChildren0_N(XodusSchoolsBy::user)

    fun packName(): UserName = UserName(firstName, middleName, lastName)
}

fun User.findInDB(store: TransientEntityStore = DATABASE): by.enrollie.eversity.data_classes.User? =
    store.transactional(readonly = true) {
        XodusUser.query(XodusUser::id eq this.id).firstOrNull()?.let {
            return@let when (UserType.valueOf(it.type.title)) {
                UserType.Pupil -> Pupil(
                    it.id,
                    it.firstName,
                    it.middleName,
                    it.lastName,
                    (it.profile as XodusPupilProfile).schoolClass.id
                )
                UserType.Parent -> Parent(it.id, it.firstName, it.middleName, it.lastName)
                UserType.Teacher, UserType.Social, UserType.Administration -> Teacher(
                    it.id,
                    it.firstName,
                    it.middleName,
                    it.lastName
                )
                UserType.SYSTEM -> object : by.enrollie.eversity.data_classes.User {
                    override val id: Int = it.id
                    override val type: UserType = UserType.SYSTEM
                    override val firstName: String = it.firstName
                    override val middleName: String? = it.middleName
                    override val lastName: String = it.lastName
                }
            }
        }
    }
