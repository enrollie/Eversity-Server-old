/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.administration

import by.enrollie.eversity.CLI
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.mojang.brigadier.CommandDispatcher

val dispatcher = CommandDispatcher<String>().apply {
    registerUserCommands()
    registerMaintenanceCommands()
    registerConfigCommands()
}

/**
 * Prints response to CLI with colored "Response: " prefix
 */
internal fun printResponse(response: String) =
    CLI.lineReader.printAbove(TextColors.blue(TextStyles.bold("Response: ")) + response)
