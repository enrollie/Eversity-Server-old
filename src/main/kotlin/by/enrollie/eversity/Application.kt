/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

@file:Suppress("DeferredResultUnused")

package by.enrollie.eversity

import by.enrollie.eversity.administration.Configurator
import by.enrollie.eversity.administration.dispatcher
import by.enrollie.eversity.controllers.PluginProvider
import by.enrollie.eversity.data_classes.SchoolNameDeclensions
import by.enrollie.eversity.database.initXodusDatabase
import by.enrollie.eversity.database.xodus_definitions.XodusAppData
import by.enrollie.eversity.placer.EversityPlacer
import by.enrollie.eversity.plugins.configureAuthentication
import by.enrollie.eversity.plugins.configureBanner
import by.enrollie.eversity.plugins.configureHTTP
import by.enrollie.eversity.plugins.installExceptionStatus
import by.enrollie.eversity.routes.*
import by.enrollie.eversity.schools_by.CredentialsChecker
import by.enrollie.eversity.security.EversityJWT
import by.enrollie.eversity.uac.OsoSchoolClass
import by.enrollie.eversity.uac.OsoUser
import by.enrollie.eversity.uac.School
import by.enrollie.eversity_plugins.plugin_api.SemVer
import by.enrollie.eversity_plugins.plugin_api.ServerConfiguration
import by.enrollie.eversity_plugins.plugin_api.TPIntegration
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.neitex.SchoolsByParser
import com.osohq.oso.Oso
import io.ktor.http.cio.websocket.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.coroutines.*
import org.jline.reader.*
import org.jline.terminal.TerminalBuilder
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Toolkit
import java.io.File
import java.lang.module.ModuleFinder
import java.net.InetAddress
import java.nio.charset.Charset
import java.nio.file.Paths
import java.time.Duration
import java.util.*
import java.util.stream.Collectors
import kotlin.io.path.createDirectory
import kotlin.io.path.notExists


var EVERSITY_PUBLIC_NAME = "Eversity "
    private set
private var EVERSITY_VERSION = ""
var EVERSITY_BUILD_DATE = ""
    private set
const val DATE_FORMAT = "YYYY-MM-dd"
lateinit var AbsencePlacer: EversityPlacer
    private set
lateinit var SchoolsCredentialsChecker: CredentialsChecker
    private set
lateinit var SCHOOL_NAME: SchoolNameDeclensions
    private set
lateinit var DATABASE: TransientEntityStore
    private set
lateinit var OSO: Oso
    private set
val DefaultDateFormatter: DateTimeFormatter = DateTimeFormat.forPattern(DATE_FORMAT)
lateinit var SERVER_CONFIGURATION: by.enrollie.eversity.database.xodus_definitions.ServerConfiguration
    private set

/**
 * Starts given action and repeats it every (repeatMillis) milliseconds
 * @param repeatMillis Periodicity of running task in milliseconds
 * @param action Action, that needs to be repeated
 */
fun CoroutineScope.launchPeriodicAsync(repeatMillis: Long, action: suspend () -> Unit) =
    this.async { //CoroutineScope extension to run task with periodicity of given repeatMillis
        while (isActive) {
            action()
            delay(repeatMillis)
        }
    }

object CLI {
    val logger: Logger = LoggerFactory.getLogger("CLI")
    private val prompt = "eversity@${InetAddress.getLocalHost().hostName} :> "
    private var isRunning = false
    private val terminal = TerminalBuilder.builder().system(true).name("Eversity Server").build()
    val lineReader: LineReader = LineReaderBuilder.builder().completer { reader, line, candidates ->
        candidates.addAll(dispatcher.getCompletionSuggestions(dispatcher.parse(line.line(), "")).get().list.map {
            Candidate(it.text)
        })
    }.appName("Eversity Server").terminal(terminal).build()
    private var job: Job? = null

    private class Console {
        fun startReader() {
            try {
                var line: String
                while (isRunning) {
                    line = try {
                        lineReader.readLine(prompt)
                    } catch (_: EndOfFileException) {
                        continue
                    }
                    processInput(line)
                }
            } catch (e: UserInterruptException) {
                stop()
                return
            }
        }

        private fun processInput(input: String) {
            if (input.isBlank()) {
                Toolkit.getDefaultToolkit().beep()
                return
            }
            try {
                val result = dispatcher.parse(input.trimStart().trimEnd(), "cli")
                dispatcher.execute(result)
            } catch (e: RuntimeException) {
                lineReader.printAbove(TextColors.red("ERROR ") + TextColors.white(TextStyles.italic("(during execution)")) + ": " + e.message)
            } catch (e: CommandSyntaxException) {
                lineReader.printAbove(TextColors.red("ERROR") + ": Brigadier did not recognize this command")
            }
        }
    }

    fun start() {
        require(!isRunning) { "CLI Reader cannot be started more than one time" }
        isRunning = true
        job = CoroutineScope(Dispatchers.IO).launch {
            Console().startReader()
        }
    }

    fun stop() {
        require(isRunning) { "CLI Reader is not running" }
        isRunning = false
        terminal.writer().println("Stopping...")
        terminal.close()
        Runtime.getRuntime().exit(0)
        job?.cancel("Stopped")
    }
}

@Suppress("CAST_NEVER_SUCCEEDS")
fun main() {
    run {
        val props = Properties()
        props.load(CLI::class.java.getResourceAsStream("/appInfo.properties"))
        EVERSITY_VERSION = props.getProperty("appVersion")
        EVERSITY_PUBLIC_NAME += EVERSITY_VERSION
        EVERSITY_BUILD_DATE = props.getProperty("buildDate")
    }
    LoggerFactory.getLogger("Bootstrap").debug("Eversity Core ${EVERSITY_VERSION}; Build date: $EVERSITY_BUILD_DATE")
    val databasePath = System.getenv("DATABASE_PATH")
        ?: error("Required system environment variable \"DATABASE_PATH\" is not defined!")
    DATABASE = initXodusDatabase(File(databasePath))
    if (!DATABASE.transactional(readonly = true) {
            XodusAppData.get().isInitialized
        }) Configurator(DATABASE).beginConfig()
    SERVER_CONFIGURATION = DATABASE.transactional(readonly = true) {
        XodusAppData.get().usableConfiguration
    }
    CLI.start()
    embeddedServer(Netty, port = SERVER_CONFIGURATION.port, module = { module() }, configure = {
        callGroupSize = 50
        workerGroupSize = 100
        requestQueueLimit = 250
        requestReadTimeoutSeconds = 5
        responseWriteTimeoutSeconds = 5
        runningLimit = 20
    }).start(wait = true)
}

fun Application.registerRoutes() {
    routing {
        authRoute()
        userRoute()
        classRoute()
        absenceRoute()
        templatingRoute()
        schoolRoute()
    }
}

fun Application.module() {
    install(io.ktor.server.plugins.ContentNegotiation) {
        json()
        checkAcceptHeaderCompliance = true
    }
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    kotlin.run {
        val namingFileName = System.getenv("SCHOOL_NAMING_FILE")
            ?: error("Required system environment variable \"SCHOOL_NAMING_FILE\" is not defined!")
        val namingFile = File(namingFileName)
        val props = Properties()
        props.load(namingFile.reader(charset("UTF-8")))
        try {
            SCHOOL_NAME = SchoolNameDeclensions(
                props.getProperty("nominative"),
                props.getProperty("genitive"),
                props.getProperty("accusative"),
                props.getProperty("dative"),
                props.getProperty("instrumental"),
                props.getProperty("prepositional"),
                props.getProperty("location")
            )
        } catch (e: NoSuchElementException) {
            throw ApplicationConfigurationException("School naming file error: ${e.message}")
        }
    }
    configureBanner()
    EversityJWT.initialize(SERVER_CONFIGURATION.jwtSecretKey)

    AbsencePlacer = EversityPlacer()
    SchoolsCredentialsChecker = CredentialsChecker(15, LoggerFactory.getLogger("SchoolsByChecker"))

    val plugins = run {
        val pluginsDir = Paths.get("plugins")
        if (pluginsDir.notExists()) {
            log.info("Plugins directory not found, creating...")
            pluginsDir.createDirectory()
            return@run listOf()
        }
        val pluginsFinder = ModuleFinder.of(pluginsDir)
        val plugins = pluginsFinder.findAll().stream().map { it.descriptor().name() }.collect(Collectors.toList())
        val pluginsConfiguration = ModuleLayer.boot().configuration().resolve(pluginsFinder, ModuleFinder.of(), plugins)
        val layer =
            ModuleLayer.boot().defineModulesWithOneLoader(pluginsConfiguration, ClassLoader.getSystemClassLoader())
        ServiceLoader.load(layer, TPIntegration::class.java).toList()
    }
    PluginProvider.setPluginServerConfiguration(
        ServerConfiguration(
            SemVer.parse(
                EVERSITY_VERSION.removePrefix("v").replaceAfter("-", "").removeSuffix("-")
            ), SERVER_CONFIGURATION.schoolWebsite, SERVER_CONFIGURATION.schoolSubdomain
        )
    )
    runBlocking {
        plugins.forEach {
            PluginProvider.registerPlugin(it)
        }
    }
    SchoolsByParser.setSubdomain(SERVER_CONFIGURATION.schoolSubdomain)
    run { //Load Oso
        OSO = Oso()
        OSO.registerClass(OsoSchoolClass::class.java, "SchoolClass")
        OSO.registerClass(OsoUser::class.java, "User")
        OSO.registerClass(School::class.java, "School")
        OSO.registerConstant(School(), "Sch")
        val polarFile = this.javaClass.getResourceAsStream("/authorization.polar")!!.readAllBytes()
            .toString(Charset.defaultCharset())
        OSO.loadStr(polarFile)
    }
    installExceptionStatus()
    configureAuthentication()
    configureHTTP()
    registerRoutes()
}
