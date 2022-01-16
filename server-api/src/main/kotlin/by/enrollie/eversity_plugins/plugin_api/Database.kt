/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity_plugins.plugin_api

interface Database {
    /**
     * Returns [User] based on user ID
     */
    fun getUserInfo(userID: Int): User?
}
