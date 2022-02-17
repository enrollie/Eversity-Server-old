/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.administration

import by.enrollie.eversity.database.xodus_definitions.XodusAppData
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import jetbrains.exodus.database.TransientEntityStore
import org.apache.commons.lang.RandomStringUtils
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.terminal.TerminalBuilder
import org.slf4j.LoggerFactory

/**
 * Class that performs first-time start configuration
 */
class Configurator(val store: TransientEntityStore) {
    private val logger = LoggerFactory.getLogger("ServerConfigurator")
    private val terminal = TerminalBuilder.builder().system(true).name("Eversity Server").build()
    private val lineReader: LineReader =
        LineReaderBuilder.builder().appName("Eversity Server").terminal(terminal).build()

    fun beginConfig() {
        lineReader.printAbove("Welcome to ${TextColors.brightRed("Eversity")}!")
        lineReader.printAbove("Would you like to set everything up right now? ${TextStyles.italic("(Note: selecting no will exit server)")}")
        run {
            var continueSelection: String
            do {
                continueSelection = lineReader.readLine("Continue? [Y/n]: ").let { it.ifBlank { "y" } }.lowercase()
            } while (continueSelection !in sequenceOf("y", "n"))
            if (continueSelection == "n") {
                logger.info("User aborted configuration, exiting...")
                Runtime.getRuntime().exit(0)
            }
        }
        lineReader.printAbove("Great! First of all, please, fill in port that server should listen on")
        val port = run {
            var port: Int?
            do {
                port = lineReader.readLine("Port (1025-25564) [8080]: ").let { it.ifBlank { "8080" } }.toIntOrNull()
            } while (port == null || port !in 1025..25564)
            port
        }
        lineReader.printAbove("Now, we need to set up your school's Schools.by subdomain! Please, enter it in \'https://*.schools.by/\' format")
        val schoolSubdomain = run {
            var subdomain: String
            do {
                subdomain = lineReader.readLine("Subdomain: ")
            } while (subdomain.isBlank() || !subdomain.matches(Regex("^https://.+\\.schools\\.by/")))
            subdomain
        }
        lineReader.printAbove("Great! We are almost there! Please, enter your school website (any format)")
        val schoolWebsite = run {
            var website: String
            do {
                website = lineReader.readLine("School website: ")
            } while (website.isBlank())
            website
        }
        lineReader.printAbove("Please, enter a directory, where Eversity can temporarily store it's reports")
        lineReader.printAbove(TextStyles.italic(TextStyles.bold("Note: ")) + TextStyles.italic(TextStyles.underline("Eversity must have at least write access to it, and your webserver must have rights to read from it")))
        val tempDir = run {
            var dir: String
            do {
                dir = lineReader.readLine("Temp directory [/tmp/eversity]: ").ifBlank { "/tmp/eversity" }
            } while (!dir.matches(Regex("^(.+)/([^/]+)\$")))
            dir
        }
        lineReader.printAbove("Please, enter document base URL (when generating reports, Eversity will respond with <your-url>/<document ID>")
        lineReader.printAbove("Format: http(s)://*.*/*")
        val baseDocumentUrl = run {
            var website: String
            do {
                website = lineReader.readLine("Base document path: ")
            } while (website.isBlank() || !website.matches(Regex("https?://(www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_+.~#?&/=]*)")))
            website
        }
        lineReader.printAbove("Please, enter document lifetime in milliseconds (after this threshold generated documents will be deleted)")
        val documentLifetime = run {
            var lifetime: Long?
            do {
                lifetime = lineReader.readLine("Lifetime (milliseconds), bigger than 1000 [20000]: ")
                    .let { it.ifBlank { "20000" } }.toLongOrNull()
            } while (lifetime == null || lifetime < 1000)
            lifetime
        }

        lineReader.printAbove("Awesome! We've got everything we need, now, let us set everything up! Thanks for using Eversity!")
        store.transactional {
            XodusAppData.get().apply {
                this.port = port
                this.schoolsBySubdomain = schoolSubdomain
                this.schoolWebsite = schoolWebsite
                this.documentTempDir = tempDir
                this.baseDocumentUrl = baseDocumentUrl
                this.documentsLifetime = documentLifetime
                jwtSecretKey = RandomStringUtils.randomAlphanumeric(128)
                isInitialized = true
            }
        }
        lineReader.printAbove("You are ready to use Eversity! We hope you enjoy it.")
        terminal.close()
        return
    }
}
