/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.placer

import by.enrollie.eversity.configSubdomainURL
import by.enrollie.eversity.data_classes.Absence
import by.enrollie.eversity.data_classes.DummyAbsence
import by.enrollie.eversity.database.functions.insertAbsences
import by.enrollie.eversity.database.functions.insertDummyAbsences
import by.enrollie.eversity.launchPeriodicAsync
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import org.joda.time.Seconds
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Engine for putting absence data to Schools.by and Eversity database
 * @author Pavel Matusevich, a.k.a. Neitex
 */
@OptIn(ObsoleteCoroutinesApi::class)
class EversityPlacer {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    var schoolsByAvailability: Boolean = true
        private set
    private var nextSchoolsByCheck: DateTime = DateTime.now()
    val nextSchoolsByCheckIn: Seconds
        get() = Seconds.secondsBetween(DateTime.now(), nextSchoolsByCheck)

    /**
     * Immediately sets absence to database
     */
    fun postAbsence(jobList: List<Absence>, dummiesJobs: List<DummyAbsence> = listOf()) {
        log.debug(
            "Placing absence list of size ${jobList.size} and classes ID {${
                jobList.map { it.classID }.toSet().joinToString { "$it; " }
            }}; List hashcode: ${jobList.hashCode()}"
        )
        insertAbsences(jobList)
        log.debug("Placed absence list of hashcode ${jobList.hashCode()}")
        if (dummiesJobs.isNotEmpty()){
            log.debug("Placing dummy absences for list of size ${dummiesJobs.size} and classes ID {${
                dummiesJobs.map { it.classID }.toSet().joinToString { "$it; " }
            }}; List hashcode: ${dummiesJobs.hashCode()}-dummy")
            insertDummyAbsences(dummiesJobs)
            log.debug("Inserted dummy absences list of hashcode ${dummiesJobs.hashCode()}-dummies")
        }
        sendAbsenceJobToSchoolsBy(jobList)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun sendAbsenceJobToSchoolsBy(jobList: List<Absence>) {
        // NO-OP until there will be some way to post absence to Schools.by
        // See https://github.com/enrollie/Eversity-Server/issues/1
    }

    @Suppress("unused")
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
                    it.get {
                        url.takeFrom(configSubdomainURL)
                    }
                }
                response.status == HttpStatusCode.OK
            } catch (e: HttpRequestTimeoutException) {
                false
            } catch (e: UnknownHostException) {
                false
            }
            logger.debug("Received Schools.by status: $availability")
            if (schoolsByAvailability != availability)
                logger.info("New Schools.by status set: $availability; Time of status check: ${DateTime.now()}")
            schoolsByAvailability = availability
            nextSchoolsByCheck = DateTime.now().plusMinutes(15)
        }
    }
}
