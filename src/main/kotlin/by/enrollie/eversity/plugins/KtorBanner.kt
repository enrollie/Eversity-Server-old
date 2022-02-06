/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.plugins

import by.enrollie.eversity.EVERSITY_PUBLIC_NAME
import by.enrollie.eversity.main
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import io.ktor.server.application.*
import team.yi.kfiglet.FigFont
import team.yi.ktor.features.banner

fun Application.configureBanner() {
    banner {
        bannerText = "Eversity Core"
        smushMode = 100
        loadFigFont = {
            val inputStream = ::main.javaClass.classLoader.getResourceAsStream("slant.flf")
            FigFont.loadFigFont(inputStream)
        }
        beforeBanner { banner ->
            Terminal().apply {
                val welcomeMessage = "Welcome to"
                println(
                    "".padStart(
                        (banner.width - welcomeMessage.length) / 2,
                        ' '
                    ) + TextColors.brightGreen(welcomeMessage) + "".padStart(
                        (banner.width - welcomeMessage.length) / 2,
                        ' '
                    )
                )
                println((TextColors.blue("".padStart(banner.width, '-'))))
            }
        }
        render {
            Terminal().println(TextColors.rgb(194, 1, 20)(it.text))
        }
        afterBanner { banner ->
            val title = " $EVERSITY_PUBLIC_NAME "
            Terminal().apply {
                println((TextColors.blue("".padStart(banner.width, '-'))))
                println(TextColors.blue("Version: ") + TextColors.brightGreen(title))
                println()
                println(TextColors.red(TextStyles.bold("! WARNING !  ") + TextStyles.italic("These logs may contain private information (like access credentials). Please, be careful on who you let to see these logs.")))
                println()
            }
        }
    }
}
