/*
 * Copyright (c) 2021.
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

plugins {
    application
    `maven-publish`
    kotlin("jvm") version "1.5.31"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.5.0"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

group = "by.enrollie.eversity"
version = getGitVersion()
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
}

dependencies {
    //----KTOR DEPENDENCIES
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-auth:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions:$ktorVersion")
    implementation("io.ktor:ktor-server-host-common:$ktorVersion")
    implementation("io.ktor:ktor-metrics:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-gson:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
    implementation("org.apache.logging.log4j:log4j-api:2.14.1")
    implementation("org.apache.logging.log4j:log4j-core:2.14.1")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.14.1")
    implementation("org.slf4j:slf4j-api:1.7.32")
    //----END OF KTOR DEPENDENCIES

    //----DATABASE DEPENDENCIES
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jodatime:$exposedVersion")
    implementation("org.postgresql:postgresql:42.3.1")
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
    implementation("it.skrape:skrapeit-core:1.0.0-alpha8")
    implementation("com.auth0:java-jwt:3.18.2")
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.0.4")
    implementation("com.github.ajalt.mordant:mordant:2.0.0-beta2")
    implementation("team.yi.ktor:ktor-banner:0.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2-native-mt") {
        version {
            strictly("1.5.2-native-mt")
        }
    }
    implementation("org.docx4j:docx4j-core:11.2.9") {
        exclude("log4j")
        exclude("org.slf4j")
    }
    implementation("org.docx4j:docx4j-openxml-objects:11.2.9") {
        exclude("log4j")
        exclude("org.slf4j")
    }
    implementation("org.docx4j:docx4j-JAXB-MOXy:11.2.9") {
        exclude("log4j")
        exclude("org.slf4j")
    }
    //----END OF OTHER DEPENDENCIES

    //----TEST DEPENDENCIES
    testImplementation("io.ktor:ktor-server-tests:$ktorVersion")
    testImplementation(kotlin("test-junit"))
    implementation("io.ktor:ktor-client-mock:$ktorVersion")
    //----END OF TEST DEPENDENCIES
    implementation(kotlin("stdlib-jdk8"))
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
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
    jvmTarget = "1.8"
}

tasks.create("stage") {
    dependsOn("shadowJar")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveClassifier.set("uberJar")
}
publishing{
    repositories {
        maven{
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/enrollie/eversity-server")
            credentials {
                username = project.findProperty("gpr.user")?.toString() ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key")?.toString() ?: System.getenv("TOKEN")
            }
        }
    }
    publications {
        register<MavenPublication>("gpr") {
            artifact(tasks["shadowJar"])
        }
    }
}
