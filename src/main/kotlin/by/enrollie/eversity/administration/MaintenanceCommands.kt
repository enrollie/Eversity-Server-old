/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.administration

import by.enrollie.eversity.CLI
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import org.joda.time.DateTime
import java.lang.management.ManagementFactory

internal fun CommandDispatcher<String>.registerMaintenanceCommands() {
    val exitCommand = literal<String>("exit").executes {
        CLI.logger.info("Shutting down gracefully because of CLI request...")
        Runtime.getRuntime().exit(0)
        0
    }
    val uwuCommand = literal<String>("uwu").executes {
        printResponse("UwU -ed at ${DateTime.now()}")
        0
    }
    val throwExceptionCommand = literal<String>("throwException").executes {
        printResponse("Throwing exception as requested...")
        throw IllegalArgumentException("Exception requested from CLI")
    }
    val resourceUsageCommand = literal<String>("resourceUsage").executes {
        val osBean = ManagementFactory.getOperatingSystemMXBean()
        val memoryBean = ManagementFactory.getMemoryMXBean()
        printResponse("Usage at ${DateTime.now()}: CPU - ${osBean.version}; RAM - ${memoryBean.heapMemoryUsage.used}")
        0
    }
    register(exitCommand)
    register(uwuCommand)
    register(throwExceptionCommand)
    register(resourceUsageCommand)
}
