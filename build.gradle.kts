/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.text.SimpleDateFormat
import java.util.*

val ktorVersion: String by project
val kotlinVersion: String by project
val logbackVersion: String by project
val exposedVersion: String by project
val xodusVersion: String by project

plugins {
    application
    `maven-publish`
    kotlin("jvm") version "1.6.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.6.10"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    id("io.wusa.semver-git-plugin") version "2.3.7"
}

group = "by.enrollie.eversity"
version = semver.info.toString().replace(Regex("\\.sha\\.[a-z0-9]{7}"), "")
application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

fun getGitVersion(): String {
    val os = org.apache.commons.io.output.ByteArrayOutputStream()
    return try {
        project.exec {
            commandLine = "git describe --tags --long".split(" ")
            standardOutput = os
        }.rethrowFailure()
        String(os.toByteArray()).trim()
    } catch (e: org.gradle.process.internal.ExecException) {
        "NON-GIT BUILD"
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://packages.neitex.me") }
    maven { url = uri("https://packages.jetbrains.team/maven/p/xodus/xodus-daily") }
    maven {
        url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
        name = "ktor-eap"
    }
}

dependencies {
    implementation(project(":server-api"))
    //----KTOR DEPENDENCIES
    implementation("org.apache.logging.log4j:log4j-api:2.17.0")
    implementation("org.apache.logging.log4j:log4j-core:2.17.0")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.17.0")
    implementation("org.slf4j:slf4j-api:1.7.33")
    //----END OF KTOR DEPENDENCIES

    //----DATABASE DEPENDENCIES
    implementation("org.jetbrains.xodus:xodus-openAPI:$xodusVersion")
    implementation("org.jetbrains.xodus:xodus-environment:$xodusVersion")
    implementation("org.jetbrains.xodus:xodus-entity-store:$xodusVersion")
    implementation("org.jetbrains.xodus:xodus-vfs:$xodusVersion")
    implementation("org.jetbrains.xodus-neitex:dnq:0.0.3")
    implementation("com.github.ben-manes.caffeine:caffeine:3.0.5")
    //----END OF DATABASE DEPENDENCIES

    //----REPORT DEPENDENCIES
    implementation("fr.opensagres.xdocreport:template:2.0.2")
    implementation("fr.opensagres.xdocreport:document:2.0.2")
    implementation("fr.opensagres.xdocreport:template:2.0.2")
    implementation("fr.opensagres.xdocreport:converter:2.0.2")
    implementation("fr.opensagres.xdocreport:fr.opensagres.xdocreport.document.docx:2.0.2")
    implementation("fr.opensagres.xdocreport:fr.opensagres.xdocreport.template.velocity:2.0.2")
    implementation("fr.opensagres.xdocreport:fr.opensagres.xdocreport.template.freemarker:2.0.2")
    implementation("org.apache.velocity:velocity:1.7")
    implementation("org.apache.commons:commons-collections4:4.4")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("oro:oro:2.0.8")
    //----END OF REPORT DEPENDENCIES


    //----OTHER DEPENDENCIES
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    implementation("com.osohq:oso:0.26.0")
    implementation("com.auth0:java-jwt:3.18.3")
    implementation("com.neitex:schools_parser:0.0.8")
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.0.4")
    implementation("com.github.ajalt.mordant:mordant:2.0.0-beta2")
    implementation("team.yi.ktor:ktor-banner:0.2.1") // From my own repository, implements changes in Ktor 2.0.0
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0-native-mt") {
        version {
            strictly("1.5.2-native-mt")
        }
    }
    implementation("org.docx4j:docx4j-core:11.3.2") {
        exclude("log4j")
        exclude("org.slf4j")
    }
    implementation("org.docx4j:docx4j-openxml-objects:11.3.2") {
        exclude("log4j")
        exclude("org.slf4j")
    }
    implementation("org.docx4j:docx4j-JAXB-MOXy:11.3.2") {
        exclude("log4j")
        exclude("org.slf4j")
    }
    implementation("net.swiftzer.semver:semver:1.2.0")
    implementation("joda-time:joda-time:2.10.13")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-default-headers:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions:$ktorVersion")
    implementation("io.ktor:ktor-server-host-common:$ktorVersion")
    implementation("io.ktor:ktor-metrics:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-mock:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    //----END OF OTHER DEPENDENCIES

    //----TEST DEPENDENCIES
    testImplementation(kotlin("test-junit"))
    //----END OF TEST DEPENDENCIES
    implementation(kotlin("stdlib-jdk8"))
    testImplementation("io.ktor:ktor-server-tests:$ktorVersion")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "11"
}
compileKotlin.dependsOn.add((tasks.getByName("processResources") as ProcessResources).apply {
    filesMatching("appInfo.properties") {
        val props = mutableMapOf<String, String>()
        props["appVersion"] = version as String
        val formatter = SimpleDateFormat("YYYY-MM-dd E, HH:mm:ss-SS")
        props["buildDate"] = formatter.format(Calendar.getInstance().time)
        expand(props)
    }
})

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "11"
}

tasks.create("stage") {
    dependsOn("shadowJar")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveClassifier.set("uberJar")
}

semver {
    snapshotSuffix = "SNAPSHOT" // (default) appended if the commit is without a release tag
    dirtyMarker = "dirty" // (default) appended if the are uncommitted changes
    initialVersion = "0.1.0" // (default) initial version in semantic versioning
    tagPrefix = "" // (default) each project can have its own tags identified by a unique prefix.
    tagType = io.wusa.TagType.LIGHTWEIGHT
    branches { // list of branch configurations
        branch {
            regex = ".+" // regex for the branch you want to configure, put this one last
            incrementer = "PATCH_INCREMENTER" // (default) version incrementer
            formatter = Transformer {
                "${semver.info.version.major}.${semver.info.version.minor}.${semver.info.version.patch}+build.${semver.info.count}"
            }
        }
    }
}
