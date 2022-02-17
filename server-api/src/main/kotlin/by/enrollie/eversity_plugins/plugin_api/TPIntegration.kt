/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity_plugins.plugin_api

import java.nio.file.Path

data class TPIntegrationMetadata(val id: String, val publicTitle: String, val version: String)

interface TPIntegration {
    val metadata: TPIntegrationMetadata
    var absenceEngine: AbsenceEngine
    var serverDatabase: Database

    /**
     * Initializes plugin, called on early stage of server start
     * @param dataPath Path, reserved to use only with this plugin
     */
    suspend fun init(
        serverConfiguration: ServerConfiguration,
        dataPath: Path,
        absenceEngine: AbsenceEngine,
        serverDatabase: Database
    ): Boolean

    /**
     * Checks, whether user is allowed to use this plugin
     */
    suspend fun checkAvailability(userID: Int, userType: UserType): Boolean

    /**
     * Requests registration of user in plugin
     * @return Map of registration challenges (i.e. pairing code)
     */
    suspend fun requestRegistration(userID: Int, userType: UserType): Map<String, String>

    /**
     * Requests deletion of user data inside of plugin
     */
    suspend fun requestDeletion(userID: Int, userType: UserType)

    /**
     * Returns list of registered users
     */
    suspend fun getRegisteredUsersList(): List<Int>
}

class BasicIntegration : TPIntegration {
    override val metadata: TPIntegrationMetadata = TPIntegrationMetadata("", "", "")
    override lateinit var absenceEngine: AbsenceEngine
    override lateinit var serverDatabase: Database
    override suspend fun init(
        serverConfiguration: ServerConfiguration,
        dataPath: Path,
        absenceEngine: AbsenceEngine,
        serverDatabase: Database
    ): Boolean {
        this.serverDatabase = serverDatabase
        this.absenceEngine = absenceEngine
        return false
    }

    override suspend fun checkAvailability(userID: Int, userType: UserType): Boolean {
        return false
    }

    override suspend fun requestRegistration(userID: Int, userType: UserType): Map<String, String> {
        return mapOf()
    }

    override suspend fun requestDeletion(userID: Int, userType: UserType) {

    }

    override suspend fun getRegisteredUsersList(): List<Int> {
        return emptyList()
    }
}
