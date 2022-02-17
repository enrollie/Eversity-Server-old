/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database.xodus_definitions

import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.*
import kotlinx.dnq.simple.min
import kotlinx.dnq.simple.regex
import kotlinx.dnq.simple.url
import kotlinx.dnq.singleton.XdSingletonEntityType
import org.apache.commons.lang.RandomStringUtils
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import java.io.File

class XodusAppData(entity: Entity) : XdEntity(entity) {
    companion object : XdSingletonEntityType<XodusAppData>() {
        override fun XodusAppData.initSingleton() {
            // Default data
            modelVersion = 1
            firstInitDate = DateTime.now()
            port = 8080
            documentTempDir = File(System.getProperty("java.io.tmpdir")).absolutePath
            documentsLifetime = 5000
            schoolWebsite = "http://localhost.local/"
            baseDocumentUrl = "http://localhost.local/"
            schoolsBySubdomain = "https://demo.schools.by/"
            jwtSecretKey = getNewJWTKey()
        }
    }

    var modelVersion by xdRequiredIntProp { min(1, "Version cannot be less than 1") }
    var firstInitDate by xdRequiredDateTimeProp { }
        private set
    var schoolsBySubdomain by xdRequiredStringProp { regex(Regex("^https://.+\\.schools\\.by/")) }
    var port by xdRequiredIntProp { }
    var jwtSecretKey by xdRequiredStringProp { }
    var schoolWebsite by xdRequiredStringProp { }
    var isInitialized by xdBooleanProp { }
    var documentTempDir by xdRequiredStringProp { regex(Regex("^.+/?[^/]?\$"), "String does not match Regex") }
    var baseDocumentUrl by xdRequiredStringProp { url() }
    var documentsLifetime by xdRequiredLongProp { min(1000) }

    override fun toString(): String = """
        modelVersion: $modelVersion
        firstInitDate (const): $firstInitDate
        schoolsBySubdomain: $schoolsBySubdomain
        port: $port
        jwtSecretKey: $jwtSecretKey
        schoolWebsite: $schoolWebsite
        documentTempDir: $documentTempDir
        baseDocumentUrl: $baseDocumentUrl
        documentsLifetime: $documentsLifetime
    """.trimIndent()

    val usableConfiguration: ServerConfiguration
        get() = ServerConfiguration(
            port,
            jwtSecretKey,
            schoolsBySubdomain,
            File(documentTempDir),
            schoolWebsite,
            baseDocumentUrl,
            documentsLifetime
        )

    fun rearmJWTSecretKey() {
        val logger = LoggerFactory.getLogger("AppConfig")
        logger.info("Rearming JWT Secret key...")
        logger.debug("Old JWT Secret key: $jwtSecretKey")
        jwtSecretKey = getNewJWTKey()
        logger.info("New JWT Secret key is issued, please, restart application to use new Secret key")
    }

    private fun getNewJWTKey(): String = RandomStringUtils.randomAlphanumeric(128)
}

data class ServerConfiguration internal constructor(
    val port: Int,
    val jwtSecretKey: String,
    val schoolSubdomain: String,
    val documentTempDir: File,
    val schoolWebsite: String,
    val baseDocumentUrl: String,
    val documentsLifetime: Long,
)
