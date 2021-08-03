/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.notifier

import by.enrollie.eversity.SCHOOL_NAME
import by.enrollie.eversity.database.functions.getTelegramNotifyList
import by.enrollie.eversity.database.functions.insertTelegramNotifyData
import by.enrollie.eversity.database.functions.isRegisteredChat
import by.enrollie.eversity.notifier.data_classes.NotifyJob
import by.enrollie.eversity.routes.telegramPairingCodesList
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.logging.LogLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*


/**
 *
 */
class EversityNotifier(telegramToken: String) {
    private val supervisorJob = SupervisorJob()
    private val notifierJob: Job
    private val bot: Bot

    private val properties: Properties

    companion object Channels {
        lateinit var notifyChannel: Channel<NotifyJob>
            private set
    }

    init {
        properties = Properties()
        properties.load(InputStreamReader(this.javaClass.getResourceAsStream("/TelegramAnswers.properties")!!, "UTF-8"))


        notifyChannel = Channel(Channel.UNLIMITED)
        bot = bot {
            token = telegramToken
            logLevel = LogLevel.All(LogLevel.Network.None)
            dispatch {
                command("start") {
                    update.consume()
                    bot.sendMessage(
                        ChatId.fromId(message.chat.id),
                        text = properties.getProperty("hello").format(SCHOOL_NAME.location, "WEBSITE PLACEHOLDER") //TODO: Ask for website
                    )
                }
                command("help") {
                    update.consume()
                    bot.sendMessage(
                        ChatId.fromId(message.chat.id),
                        text = if (!isRegisteredChat(message.chat.id)) properties.getProperty("help_not_registered")
                            .format(SCHOOL_NAME.location) else properties.getProperty("help_registered")
                    )
                }
                text {
                    val code = this.message.text?.toShortOrNull()
                    val foundCode = telegramPairingCodesList.find { it.first == code }
                    if (foundCode == null) {
                        bot.sendMessage(
                            ChatId.fromId(message.chat.id),
                            text = properties.getProperty("sync_code_unknown")
                        )
                        return@text
                    }
                    insertTelegramNotifyData(foundCode.second.first, foundCode.second.second, message.chat.id)
                    bot.sendMessage(ChatId.fromId(message.chat.id), text = properties.getProperty("sync_successful"))
                    telegramPairingCodesList.remove(foundCode)
                }
            }
        }
        bot.startPolling()
        notifierJob = CoroutineScope(Dispatchers.IO + supervisorJob).launch {
            while (true) {
                val notifyJob = notifyChannel.receive()
                launch {
                    val notifyList = getTelegramNotifyList(notifyJob.pupil.id).toSet()
                    val notifyDate =
                        if (SimpleDateFormat("YYYY-MM-dd").format(Calendar.getInstance().time) == notifyJob.date) null else SimpleDateFormat(
                            "yyyy-MM-dd"
                        ).parse(notifyJob.date)
                    notifyList.forEach {
                        val message =
                            if (Calendar.getInstance()[Calendar.HOUR_OF_DAY] >= 12)
                                properties.getProperty("kid_absent_day")
                                    .format(
                                        "${notifyJob.pupil.firstName} ${notifyJob.pupil.lastName}",
                                        if (notifyDate == null) "" else " ${
                                            SimpleDateFormat("dd MMMM yyyy", Locale("ru")).format(notifyDate)
                                        } года"
                                    )
                            else properties.getProperty("kid_absent_morning")
                                .format(
                                    "${notifyJob.pupil.firstName} ${notifyJob.pupil.lastName}",
                                    if (notifyDate == null) "" else " ${
                                        SimpleDateFormat("dd MMMM yyyy", Locale("ru")).format(notifyDate)
                                    } года"
                                )

                        bot.sendMessage(ChatId.fromId(it), message)
                    }
                }
            }
        }
    }
}