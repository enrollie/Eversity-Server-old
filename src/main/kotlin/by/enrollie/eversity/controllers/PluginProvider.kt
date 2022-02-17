/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.controllers

import by.enrollie.eversity.data_classes.UserID
import by.enrollie.eversity.database.databasePluginInterface
import by.enrollie.eversity.database.functions.absenceEngineImpl
import by.enrollie.eversity.database.functions.getUserType
import by.enrollie.eversity_plugins.plugin_api.ServerConfiguration
import by.enrollie.eversity_plugins.plugin_api.TPIntegration
import by.enrollie.eversity_plugins.plugin_api.UserType
import org.slf4j.LoggerFactory
import kotlin.io.path.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.notExists

object PluginProvider {
    lateinit var serverConfiguration: ServerConfiguration
        private set

    fun setPluginServerConfiguration(configuration: ServerConfiguration) {
        serverConfiguration = configuration
    }

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val pluginsList = mutableListOf<TPIntegration>()
    suspend fun registerPlugin(plugin: TPIntegration) {
        logger.info("Registering plugin with ID \'${plugin.metadata.id}\'; version \'${plugin.metadata.version}\'; public name \'${plugin.metadata.publicTitle}\'")
        val pluginDataPath = run {
            Path("plugins", plugin.metadata.id).apply {
                if (notExists())
                    createDirectory()
            }
        }
        val initResult = try {
            plugin.init(serverConfiguration, pluginDataPath, absenceEngineImpl, databasePluginInterface)
            null
        } catch (e: Exception) {
            e
        }
        if (initResult == null) {
            logger.debug("Plugin \'${plugin.metadata.id}\' successfully initialized")
            pluginsList.add(plugin)
        } else {
            logger.error("Plugin \'${plugin.metadata.id}\' returned failed initialization", initResult)
            error("Plugin with ID \'${plugin.metadata.id}\' did not initialize and returned exception \'${initResult.message}\'")
        }
    }

    suspend fun getAvailableIntegrationsForUser(userID: UserID): List<TPIntegration> = pluginsList.filter {
        it.checkAvailability(
            userID, UserType.valueOf(
                getUserType(userID).name.uppercase()
            )
        )
    }

    suspend fun getRegisteredIntegrations(userID: UserID): List<TPIntegration> = pluginsList.filter {
        it.getRegisteredUsersList().contains(userID)
    }

    suspend fun requestRegistration(userID: UserID, integrationID: String): Map<String, String> {
        requireNotNull(getAvailableIntegrationsForUser(userID).find { it.metadata.id == integrationID })
        return pluginsList.find { it.metadata.id == integrationID }!!.requestRegistration(
            userID, UserType.valueOf(
                getUserType(userID).name.uppercase()
            )
        )
    }

    suspend fun requestDeletion(userID: UserID, integrationID: String) {
        pluginsList.find { it.metadata.id == integrationID }
            ?.requestDeletion(userID, UserType.valueOf(getUserType(userID).name.uppercase()))
    }
}
