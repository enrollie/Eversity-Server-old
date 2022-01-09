/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.placer

import by.enrollie.eversity.configSubdomainURL
import by.enrollie.eversity.data_classes.Absence
import by.enrollie.eversity.database.functions.insertAbsences
import by.enrollie.eversity.database.functions.insertDummyAbsence
import by.enrollie.eversity.launchPeriodicAsync
import by.enrollie.eversity.placer.data_classes.PlaceJob
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Engine for putting absence data to Schools.by and Eversity database
 * @author Pavel Matusevich, a.k.a. Neitex
 */
@OptIn(ObsoleteCoroutinesApi::class)
class EversityPlacer(logger: Logger) {

    private var _schoolsByAvailable = true
    private val log: Logger = logger

    val schoolsByStatusChannel = BroadcastChannel<Boolean>(Channel.UNLIMITED)
    var schoolsByAvailability: Boolean = true
        private set
    var nextSchoolsByCheck: DateTime = DateTime.now()
        private set

    /**
     * Immediately sets absence to database
     */
    fun postAbsence(jobList: List<PlaceJob>) {
        log.debug(
            "Placing absence list of size ${jobList.size} and classes ID {${
                jobList.map { it.pupil.classID }.toSet().joinToString { "$it; " }
            }}; List hashcode: ${jobList.hashCode()}"
        )
        insertAbsences(jobList.filter { it.reason != null }.map {
            Absence(
                it.pupil.id,
                it.pupil.classID,
                it.postedBy,
                it.date.withTimeAtStartOfDay(),
                it.reason!!, it.absenceList, it.additionalNotes
            )
        })
        jobList.filter { it.reason == null }.takeIf { it.isNotEmpty() }?.forEach {
            insertDummyAbsence(it.pupil.classID, it.date.withTimeAtStartOfDay())
        }
        log.debug("Placed absence list of hashcode ${jobList.hashCode()}")
        sendAbsenceJobToSchoolsBy(jobList)
    }

    private fun sendAbsenceJobToSchoolsBy(jobList: List<PlaceJob>) {
        // NO-OP until there will be some way to post absence to Schools.by
        // See https://github.com/enrollie/Eversity-Server/issues/1
    }

    private val schoolsByCheckerJob = CoroutineScope(Dispatchers.IO).launch {
        val logger = LoggerFactory.getLogger("SchoolsByChecker")
        launchPeriodicAsync(TimeUnit.MINUTES.toMillis(15)) {
            val availability = try {
                val response = HttpClient {
                    this.expectSuccess = false
                    install(HttpTimeout) {
                        requestTimeoutMillis = TimeUnit.SECONDS.toMillis(30.toLong())
                        connectTimeoutMillis = TimeUnit.SECONDS.toMillis(30.toLong())
                        socketTimeoutMillis = TimeUnit.SECONDS.toMillis(30.toLong())
                    }
                }.use {
                    it.get<HttpResponse> {
                        url.takeFrom(configSubdomainURL!!)
                    }
                }
                response.status == HttpStatusCode.OK
            } catch (e: HttpRequestTimeoutException) {
                false
            } catch (e: UnknownHostException) {
                false
            }
            logger.debug("Received Schools.by status: $availability")
            schoolsByStatusChannel.send(availability)
            if (schoolsByAvailability != availability)
                logger.info("New Schools.by status set: $availability; Time of status check: ${DateTime.now()}")
            schoolsByAvailability = availability
            nextSchoolsByCheck = DateTime.now().plusMinutes(15)
        }
    }
}
