/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.xodus_definitions

import by.enrollie.eversity.data_classes.UserName
import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.*
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
    val profile by xdChild1(XodusBaseUserProfile::user)
    val accessTokens by xdChildren0_N(XodusToken::user)
    val schoolsByCredentials by xdChildren0_N(XodusSchoolsBy::user)

    fun packName(): UserName = UserName(firstName, middleName, lastName)
}
