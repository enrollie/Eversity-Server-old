/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.schools_by

import by.enrollie.eversity.N_Placer
import by.enrollie.eversity.database.functions.getAllCredentials
import by.enrollie.eversity.database.functions.invalidateAllTokens
import by.enrollie.eversity.launchPeriodicAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.joda.time.DateTime
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.TimeUnit
import javax.naming.AuthenticationException

/**
 * Checks Schools.by credentials automatically
 * @author Pavel Matusevich
 */
class CredentialsChecker(periodicity: Int, log: Logger) {
    private var checkPeriodicity: Int = periodicity
    private val logger = log

    private val checkerScope: CoroutineScope
    private val checkerFunction: suspend () -> Unit
    private var checkerJob: Job
    var lastCheckRemoved: Pair<DateTime, Int>
        private set

    init {
        lastCheckRemoved = Pair(DateTime.now(), 0)
        checkerScope = CoroutineScope(Dispatchers.Default)
        checkerFunction = {
            logger.info("Starting Schools.by credentials validity auto-check")
            if (!N_Placer.getSchoolsByAvailability()) {
                logger.warn("Current Schools.by credentials auto-check will be skipped because Schools.by is unavailable")
            }
            val queue: Queue<Pair<Int, Triple<String?, String?, String>>> = LinkedList(getAllCredentials())
            var invalidatedCounter = 0
            while (queue.isNotEmpty() && N_Placer.getSchoolsByAvailability()) {
                val checkingCredentials = queue.poll()
                if (checkingCredentials.second.first == null || checkingCredentials.second.second == null) {
                    if (invalidateAllTokens(checkingCredentials.first, "AUTO_COOKIES_NOT_VALID") != 0) {
                        logger.info("Invalidated tokens for user ID ${checkingCredentials.first} because one of cookies were null")
                        invalidatedCounter++
                    }
                    continue
                }
                val result = try {
                    SchoolsAPIClient(checkingCredentials.second.third).getCurrentUserData()
                    SchoolsWebWrapper().validateCookies(
                        Pair(
                            checkingCredentials.second.first!!,
                            checkingCredentials.second.second!!
                        )
                    )
                } catch (e: AuthenticationException) {
                    false
                } catch (e: UnknownError) {
                    false
                }
                if (!result) {
                    if (invalidateAllTokens(checkingCredentials.first, "AUTO_COOKIES_NOT_VALID") != 0) {
                        logger.info("Invalidated tokens for user ID ${checkingCredentials.first} because auto-check failed for Schools.by credentials")
                        invalidatedCounter++
                    }
                    continue
                }
            }
            lastCheckRemoved = Pair(DateTime.now(), invalidatedCounter)
            if (invalidatedCounter != 0 && N_Placer.getSchoolsByAvailability()) {
                logger.info("During regular Schools.by credentials validation Eversity tokens were invalidated for $invalidatedCounter users")
            }
        }
        checkerJob = checkerScope.launchPeriodicAsync(
            TimeUnit.MINUTES.toMillis(checkPeriodicity.toLong()),
            action = checkerFunction
        )
    }

    /**
     * Cancels ongoing check job and re-starts it
     */
    fun forceRecheck() {
        checkerJob.cancel("Force recheck requested")
        checkerJob = checkerScope.launchPeriodicAsync(TimeUnit.MINUTES.toMillis(checkPeriodicity.toLong())) {
            checkerFunction
        }
    }
}