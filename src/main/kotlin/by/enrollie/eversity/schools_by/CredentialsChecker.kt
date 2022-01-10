/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.schools_by

import by.enrollie.eversity.AbsencePlacer
import by.enrollie.eversity.database.functions.deleteSchoolsByCredentials
import by.enrollie.eversity.database.functions.getAllSchoolsByCredentials
import by.enrollie.eversity.database.functions.invalidateAllTokens
import by.enrollie.eversity.launchPeriodicAsync
import com.neitex.Credentials
import com.neitex.SchoolsByParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.joda.time.DateTime
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.TimeUnit

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
            if (!AbsencePlacer.schoolsByAvailability) {
                logger.warn("Current Schools.by credentials auto-check will be skipped because Schools.by is unavailable")
            }
            val queue: Queue<Pair<Int, Pair<String, String>>> = LinkedList(getAllSchoolsByCredentials())
            var invalidatedCounter = 0
            while (queue.isNotEmpty() && AbsencePlacer.schoolsByAvailability) {
                val checkingCredentials = queue.poll()
                val result = SchoolsByParser.AUTH.checkCookies(
                    Credentials(
                        checkingCredentials.second.first,
                        checkingCredentials.second.second
                    )
                )
                if (result.isSuccess && result.getOrNull() == false) {
                    log.debug("Invalidated tokens for user with ID ${checkingCredentials.first} due to invalid Schools.by credentials")
                    deleteSchoolsByCredentials(checkingCredentials.first)
                    invalidateAllTokens(checkingCredentials.first)
                    invalidatedCounter++
                    continue
                }
            }
            lastCheckRemoved = Pair(DateTime.now(), invalidatedCounter)
            if (invalidatedCounter != 0 && AbsencePlacer.schoolsByAvailability) {
                logger.info("During regular Schools.by credentials validation Eversity tokens were invalidated for $invalidatedCounter users")
            }
        }
        checkerJob = checkerScope.launchPeriodicAsync(
            TimeUnit.MINUTES.toMillis(checkPeriodicity.toLong()),
            action = checkerFunction
        )
    }
}
