/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.plugins

import by.enrollie.eversity.EVERSITY_PUBLIC_NAME
import by.enrollie.eversity.EVERSITY_WEBSITE
import by.enrollie.eversity.main
import io.ktor.application.*
import team.yi.kfiglet.FigFont
import team.yi.ktor.features.banner

fun Application.configureBanner(){
    banner {
        bannerText = "Eversity Core"
        smushMode = 100
        loadFigFont = {
            val inputStream = ::main.javaClass.classLoader.getResourceAsStream("slant.flf")
            FigFont.loadFigFont(inputStream)
        }
        beforeBanner {banner->
            val welcome = "    WORK IN PROGRESS VERSION    " //TODO: Remove after release
            val filling = "".padStart(banner.width/2 - (welcome.length /2),'-') + welcome + "".padStart(banner.width/2 - (welcome.length /2),'-')
            println(filling)
        }
        render {
            println(it.text)
        }
        afterBanner { banner ->
            val title = " $EVERSITY_PUBLIC_NAME "
            val homepage = " $EVERSITY_WEBSITE "
            val filling = "".padEnd(banner.width - title.length - homepage.length, ' ')

            println("".padEnd(banner.width, '-'))
            println("$title$filling$homepage")
        }
    }
}