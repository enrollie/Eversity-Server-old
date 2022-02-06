/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.administration

import by.enrollie.eversity.CLI
import by.enrollie.eversity.DATABASE
import by.enrollie.eversity.database.functions.ServerConfigurationEntry
import by.enrollie.eversity.database.functions.getConfigEntry
import by.enrollie.eversity.database.functions.setConfigEntry
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType.getString
import com.mojang.brigadier.arguments.StringArgumentType.string
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument

@Suppress("UNCHECKED_CAST")
private fun <B : Any> castedSetConfigEntry(entry: ServerConfigurationEntry<B>, newValue: Any) =
    setConfigEntry(entry, newValue as B, DATABASE)

internal fun CommandDispatcher<String>.registerConfigCommands() {
    val setConfigCommand = literal<String>("setConfig").then(
        argument<String?, String?>("entry", string()).suggests { _, builder ->
            builder.apply {
                ServerConfigurationEntry.validInputs.forEach {
                    suggest(it)
                }
            }.buildFuture()
        }.then(
            argument<String?, String?>("value", string()).suggests { context, builder ->
                val entry = getString(context, "entry")
                if (!ServerConfigurationEntry.checkValidity(entry))
                    throw IllegalArgumentException("$entry is not a valid entry")
                return@suggests when (entry) {
                    "schoolsBySubdomain" -> builder.suggest("<url>") { "https://*.schools.by/" }.buildFuture()
                    "port" -> builder.suggest("<int>") { " int > 1024" }.buildFuture()
                    "rearmJWTSecretKey" -> builder.suggest("<any>") { "Any input will trigger rearm" }.buildFuture()
                    "schoolWebsite" -> builder.suggest("<url>") { "URL of your school" }.buildFuture()
                    "documentTempDir" -> builder.suggest("<dir>") { "Document dir for Eversity to store reports" }
                        .buildFuture()
                    "baseDocumentUrl" -> builder.suggest("<url>") { "Base URl when Eversity offers user to download a document" }
                        .buildFuture()
                    "documentsLifetime" -> builder.suggest("<long>") { "Lifetime of documents in seconds" }
                        .buildFuture()
                    else -> error("Illegal entry $entry")
                }

            }.executes { context ->
                val entry = getString(context, "entry")
                if (!ServerConfigurationEntry.checkValidity(entry))
                    throw IllegalArgumentException("$entry is not a valid entry")
                val value = getString(context, "value")
                val serverConfEntry: Pair<ServerConfigurationEntry<*>, *> = when (entry) {
                    "schoolsBySubdomain" -> Pair(ServerConfigurationEntry.SchoolsBySubdomain, value)
                    "port" -> Pair(ServerConfigurationEntry.Port, value.toInt())
                    "rearmJWTSecretKey" -> Pair(ServerConfigurationEntry.rearmJWTSecretKey, Unit)
                    "schoolWebsite" -> Pair(ServerConfigurationEntry.schoolWebsite, value)
                    "documentTempDir" -> Pair(ServerConfigurationEntry.documentTempDir, value)
                    "baseDocumentUrl" -> Pair(ServerConfigurationEntry.baseDocumentUrl, value)
                    "documentsLifetime" -> Pair(ServerConfigurationEntry.documentsLifetime, value.toLong())
                    else -> throw IllegalArgumentException("Entry $entry is invalid")
                }
                if (serverConfEntry.first.entry != ServerConfigurationEntry.rearmJWTSecretKey.entry)
                    CLI.lineReader.printAbove(TextColors.blue(TextStyles.bold("INFO: ")) + "Current value: ${
                        getConfigEntry(serverConfEntry.first)
                    }")
                CLI.lineReader.printAbove(TextColors.blue(TextStyles.bold("INFO: ")) + "Setting to: $serverConfEntry")
                val acceptance = CLI.lineReader.readLine("Continue? [y/N]: ").lowercase().ifBlank { "n" }
                if (acceptance == "y") {
                    castedSetConfigEntry(serverConfEntry.first, serverConfEntry.second!!)
                }
                0
            }
        )
    )
    val getConfigCommand =
        literal<String>("getConfig").then(argument<String?, String?>("entry", string()).suggests { _, builder ->
            builder.apply {
                ServerConfigurationEntry.validInputs.filter {
                    it != "rearmJWTSecretKey" // Rearming JWT Secret Key is write-only
                }.forEach {
                    suggest(it)
                }
            }.buildFuture()
        }.executes { context ->
            val serverConfEntry: ServerConfigurationEntry<*> = when (val entry = getString(context, "entry")) {
                "schoolsBySubdomain" -> ServerConfigurationEntry.SchoolsBySubdomain
                "port" -> ServerConfigurationEntry.Port
                "schoolWebsite" -> ServerConfigurationEntry.schoolWebsite
                "documentTempDir" -> ServerConfigurationEntry.documentTempDir
                "baseDocumentUrl" -> ServerConfigurationEntry.baseDocumentUrl
                "documentsLifetime" -> ServerConfigurationEntry.documentsLifetime
                "rearmJWTSecretKey" -> throw IllegalArgumentException("Entry rearmJWTSecretKey is write-only")
                else -> throw IllegalArgumentException("Entry $entry is invalid")
            }
            printResponse(getConfigEntry(serverConfEntry).toString())
            0
        })
    register(getConfigCommand)
    register(setConfigCommand)
}
