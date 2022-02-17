/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.functions

import by.enrollie.eversity.DATABASE
import by.enrollie.eversity.database.xodus_definitions.XodusAppData
import jetbrains.exodus.database.TransientEntityStore
import org.slf4j.LoggerFactory

@Suppress("UNUSED")
class ServerConfigurationEntry<B : Any> private constructor(var entry: String) {
    companion object {
        val SchoolsBySubdomain: ServerConfigurationEntry<String>
            get() = ServerConfigurationEntry("schoolsBySubdomain")
        val Port: ServerConfigurationEntry<Int>
            get() = ServerConfigurationEntry("port")
        val rearmJWTSecretKey: ServerConfigurationEntry<Unit>
            get() = ServerConfigurationEntry("rearmJWTSecretKey")
        val schoolWebsite: ServerConfigurationEntry<String>
            get() = ServerConfigurationEntry("schoolWebsite")
        val documentTempDir: ServerConfigurationEntry<String>
            get() = ServerConfigurationEntry("documentTempDir")
        val baseDocumentUrl: ServerConfigurationEntry<String>
            get() = ServerConfigurationEntry("baseDocumentUrl")
        val documentsLifetime: ServerConfigurationEntry<Long>
            get() = ServerConfigurationEntry("documentsLifetime")
        val validInputs = sequenceOf("schoolsBySubdomain",
            "port",
            "rearmJWTSecretKey",
            "schoolWebsite",
            "documentTempDir",
            "baseDocumentDir",
            "documentsLifetime")

        fun checkValidity(input: String): Boolean = input in validInputs
    }

    override fun toString(): String = entry
}

@Suppress("UNCHECKED_CAST") // Every entry is known from previous class
fun <B : Any> setConfigEntry(entry: ServerConfigurationEntry<B>, newValue: B, store: TransientEntityStore = DATABASE) {
    val logger = LoggerFactory.getLogger("AppConfig")
    logger.info("Applying entry: {$entry, $newValue}")
    store.transactional {
        XodusAppData.get().apply {
            when (entry.entry) {
                "schoolsBySubdomain" -> schoolsBySubdomain = newValue as String
                "port" -> port = newValue as Int
                "rearmJWTSecretKey" -> rearmJWTSecretKey()
                "schoolWebsite" -> schoolWebsite = newValue as String
                "documentTempDir" -> documentTempDir = newValue as String
                "baseDocumentUrl" -> baseDocumentUrl = newValue as String
                "documentsLifetime" -> documentsLifetime = newValue as Long
                else -> throw IllegalArgumentException("${entry.entry} is not a valid entry")
            }
        }
    }
    logger.info("Entry {$entry, $newValue} updated, restart server for changes to take effect")
}

@Suppress("UNCHECKED_CAST")
fun <B : Any> getConfigEntry(
    entry: ServerConfigurationEntry<B>,
    store: TransientEntityStore = DATABASE,
): Pair<ServerConfigurationEntry<B>, B> =
    store.transactional(readonly = true) {
        XodusAppData.get().let {
            when (entry.entry) {
                "schoolsBySubdomain" -> {
                    Pair(entry, it.schoolsBySubdomain as B)
                }
                "port" -> {
                    Pair(entry, it.port as B)
                }
                "rearmJWTSecretKey" -> error("This entry is read-only")
                "schoolWebsite" -> {
                    Pair(entry, it.schoolWebsite as B)
                }
                "documentTempDir" -> {
                    Pair(entry, it.documentTempDir as B)
                }
                "baseDocumentUrl" -> {
                    Pair(entry, it.baseDocumentUrl as B)
                }
                "documentsLifetime" -> {
                    Pair(entry, it.documentsLifetime as B)
                }
                else -> throw IllegalArgumentException("${entry.entry} is not a valid entry")
            }
        }
    }
