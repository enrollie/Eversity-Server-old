/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.placer

import by.enrollie.eversity.data_classes.AbsenceReason
import by.enrollie.eversity.database.functions.insertAbsence
import by.enrollie.eversity.database.functions.removeAbsence
import by.enrollie.eversity.exceptions.SchoolsByNotAvailableException
import by.enrollie.eversity.notifier.EversityNotifier
import by.enrollie.eversity.notifier.data_classes.NotifyJob
import by.enrollie.eversity.placer.data_classes.PlaceJob
import by.enrollie.eversity.placer.data_classes.PlacingStatus
import by.enrollie.eversity.schools_by.SchoolsPlacer
import by.enrollie.eversity.schools_by.SchoolsWebWrapper
import io.ktor.util.*
import kotlinx.coroutines.*
import org.joda.time.DateTime
import org.slf4j.Logger
import java.util.*
import javax.security.auth.login.CredentialException

/**
 * Engine for putting absence data to Schools.by and Eversity database
 * @author Pavel Matusevich, a.k.a. Neitex
 */
class EversityPlacer(logger: Logger) {

    private var _schoolsByAvailable = true
    private val log: Logger = logger
    private val _jobStatuses = mutableMapOf<String, PlacingStatus>()
    private val _jobGroups = mutableMapOf<String, List<String>>()

    private val supervisorJob = SupervisorJob()
    private val placerEngine: Job

    private var queue: Queue<Pair<String, PlaceJob>> = LinkedList() //Placement queue

    init {
        placerEngine = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                if (queue.isNotEmpty()) {
//                    if (_schoolsByAvailable) {
                    val element = queue.poll()
                    withContext(Dispatchers.IO + supervisorJob) {
                        launch(CoroutineExceptionHandler { _, throwable ->
                            _jobStatuses[element.first] = PlacingStatus.ERROR
                            log.error(throwable)
                        }) {
                            _jobStatuses[element.first] = PlacingStatus.RUNNING
                            val absenceResult = setAbsence(element.second)
                            if (absenceResult != null) {
                                _jobStatuses[element.first] = PlacingStatus.ERROR
                            } else {
                                _jobStatuses[element.first] = PlacingStatus.DONE
                            }
                        }
                    }
//                    }
                }
                delay(150)
            }
        }
        placerEngine.invokeOnCompletion {
            if (it != null) {
                logger.error(it)
            }
        }
    }

    /**
     * Sets if Schools.by is available and ready to receive marks
     * @param available Availability of Schools.by
     */
    fun setSchoolsByAvailability(available: Boolean) {
        if (available != _schoolsByAvailable) log.debug("EversityPlacer: Schools.by availability is set to $available")
        _schoolsByAvailable = available
    }

    /**
     * Gets Schools.by availability
     */
    fun getSchoolsByAvailability() = _schoolsByAvailable

    /**
     * Adds job of placing absence. Before adding job, checks for validity of given credentials
     * @param job Placing job
     * @return ID of created job (starts with "jb")
     * @throws CredentialException Thrown, if given credentials are invalid
     */
    suspend fun addPlacementJob(job: PlaceJob): String {
        if (_schoolsByAvailable && !SchoolsWebWrapper().validateCookies(job.credentials.first)) {
            log.debug("Credentials of added job are invalid")
            throw CredentialException("Credentials for job are invalid")
        } else if (!_schoolsByAvailable) {
            log.debug("Credential check for absence job skipped as Schools.by is not available")
        }
        var generatedUUID = UUID.randomUUID()
        if (_jobStatuses.containsKey("jb$generatedUUID"))
            generatedUUID = UUID.randomUUID()
        _jobStatuses["jb$generatedUUID"] = PlacingStatus.WAITING
        queue.offer(Pair("jb$generatedUUID", job))
        return "jb$generatedUUID"
    }

    /**
     * Adds list of jobs to adding queue. All of jobs credentials required to be the same.
     * Returns job group ID (starts with "gr").
     * @see checkJobGroupStatus
     * @param jobList Not empty list of placement jobs
     * @return Jobs group ID (starts with "gr")
     * @throws IllegalArgumentException Thrown, if job list is empty OR credentials of jobs list are not the same
     * @throws CredentialException Thrown, if list credentials are invalid
     */
    suspend fun batchPlacementJobs(jobList: List<PlaceJob>): String {
        require(jobList.count { it.credentials == jobList.first().credentials } == jobList.size) { "Job list credentials are not the same" }
        if (!SchoolsWebWrapper().validateCookies(jobList.first().credentials.first)) {
            log.debug("Credentials of added job are invalid")
            throw CredentialException("Credentials for job are invalid")
        }
        val jobsID = mutableListOf<String>()
        jobList.forEach {
            var generatedUUID = UUID.randomUUID()
            if (_jobStatuses.containsKey("jb$generatedUUID"))
                generatedUUID = UUID.randomUUID()
            _jobStatuses["jb$generatedUUID"] = PlacingStatus.WAITING
            queue.offer(Pair("jb$generatedUUID", it))
            jobsID += "jb$generatedUUID"
        }
        var generatedUUID = UUID.randomUUID()
        if (_jobStatuses.containsKey("gr$generatedUUID"))
            generatedUUID = UUID.randomUUID()
        _jobGroups["gr$generatedUUID"] = jobsID
        return "gr$generatedUUID"
    }

    /**
     * Checks status of SINGLE job
     * @see addPlacementJob
     * @param jobID Job ID starting with "jb"
     * @return Current status of given job
     * @throws IllegalArgumentException Thrown, if job with given ID does not exist (also thrown when job ID does not start with "jb", as jobs array does not have other prefixes)
     */
    fun checkJobStatus(jobID: String): PlacingStatus {
        require(_jobStatuses.containsKey(jobID)) { "Job with ID $jobID was not found" }
        return _jobStatuses.getOrDefault(jobID, PlacingStatus.ERROR)
    }

    /**
     * Checks status of group job. Returns PlacingStatus mapped to list of jobs with same status
     * @see batchPlacementJobs
     * @param jobGroupID Job group ID (starts with "gr")
     * @throws IllegalArgumentException Thrown, if job group ID was not found
     */
    fun checkJobGroupStatus(jobGroupID: String): Map<PlacingStatus, List<String>> {
        require(_jobGroups.containsKey(jobGroupID)) { "Group list does not contain group ID $jobGroupID" }
        val resultMap = mutableMapOf<PlacingStatus, List<String>>()
        for (i in PlacingStatus.values()) {
            resultMap[i] = listOf()
        }
        val jobsList = _jobGroups[jobGroupID]!!
        for (job in jobsList) {
            val currentJobStatus = checkJobStatus(job)
            resultMap[currentJobStatus] = resultMap[currentJobStatus]?.plus(job)!!
        }
        return resultMap
    }

    /**
     * Sets absence to Schools.by and Eversity database
     * @param placeJob Placement job
     * @return Exception if thrown or null
     */
    private suspend fun setAbsence(placeJob: PlaceJob): Exception? {
        if (placeJob.pupil.id == -1) {
            if (placeJob.absenceList.contains(Pair(0, true)) || placeJob.absenceList.contains(Pair(1, true))) {
                insertAbsence(placeJob.pupil, AbsenceReason.UNKNOWN, DateTime.parse(placeJob.date))
            } else {
                if (placeJob.absenceList.contains(Pair(0, false)) || placeJob.absenceList.contains(Pair(1, false)))
                    removeAbsence(placeJob.pupil, DateTime.parse(placeJob.date))
            }
            return null
        }
        val credentials = placeJob.credentials
        val placer = SchoolsPlacer(credentials.first)
        var exception: Exception? = null
        if (!_schoolsByAvailable)
            exception = SchoolsByNotAvailableException("Schools.by is not available at the start of absence setter")
        if (exception == null && !placer.validateCookies(changeInternal = false)) {
            exception = CredentialException("Credentials of job from queue are invalid. Pupil: ${placeJob.pupil};")
        }
        if (exception == null) {
            val schoolsPlacement =
                placer.placeAbsence(placeJob.pupil, placeJob.absenceList, credentials.second, placeJob.date)
            if (schoolsPlacement.first != 0) {
                log.warn(
                    "Schools placer failed of placing absence for next lessons: ${
                        schoolsPlacement.second?.joinToString(
                            ","
                        )
                    }; Pupil: ${placeJob.pupil};"
                )
            }
        } else {
            log.warn(
                "Absence for pupil ${placeJob.pupil} has not been placed to Schools.by, as exception was thrown before beginning of placing (Exception: ${exception.localizedMessage}; Stack trace: ${
                    exception.stackTrace.joinToString(
                        "\t\t\n"
                    )
                }"
            )
        }
        if (placeJob.absenceList.contains(Pair(0, true)) || placeJob.absenceList.contains(Pair(1, true))) {
            insertAbsence(placeJob.pupil, placeJob.reason, DateTime.parse(placeJob.date))
            EversityNotifier.notifyChannel.send(
                NotifyJob(
                    placeJob.pupil,
                    placeJob.date,
                    placeJob.absenceList.map { it.first })
            )
        } else {
            if (placeJob.absenceList.contains(Pair(0, false)) || placeJob.absenceList.contains(Pair(1, false)))
                removeAbsence(placeJob.pupil, DateTime.parse(placeJob.date))
        }
        return exception
    }

}