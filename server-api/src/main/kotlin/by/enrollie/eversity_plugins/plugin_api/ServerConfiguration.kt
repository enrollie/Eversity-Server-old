/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity_plugins.plugin_api

data class ServerConfiguration(
    /**
     * Server version
     */
    val version: SemVer,
    /**
     * Represents school's website
     */
    val schoolWebsite: String,
    /**
     * School's Schools.by website address
     */
    val schoolSchoolsByAddress: String
)
