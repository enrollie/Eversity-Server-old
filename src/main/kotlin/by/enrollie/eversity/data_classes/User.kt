/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import by.enrollie.eversity.database.xodus_definitions.XodusUser
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.query.eq
import kotlinx.dnq.query.firstOrNull
import kotlinx.dnq.query.query

interface User {
    val id: Int
    val type: UserType
    val firstName: String
    val middleName: String?
    val lastName: String

    companion object {
        fun getByID(store: TransientEntityStore, id: Int) = store.transactional {
            XodusUser.query(XodusUser::id eq id).firstOrNull()?.let {
                object : User {
                    override val id: Int = it.id
                    override val type: UserType = UserType.valueOf(it.type.title)
                    override val firstName: String = it.firstName
                    override val middleName: String? = it.middleName
                    override val lastName: String = it.lastName
                }
            }
        }
    }
}
