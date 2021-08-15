import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.text.SimpleDateFormat
import java.util.*

val ktorVersion: String by project
val kotlinVersion: String by project
val logbackVersion: String by project
val exposedVersion: String by project

plugins {
    application
    kotlin("jvm") version "1.5.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.5.0"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    id("org.jetbrains.dokka") version "1.4.32"
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
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("org.apache.logging.log4j:log4j-api:2.9.1")
    implementation("org.apache.logging.log4j:log4j-core:2.9.1")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.14.1")
    implementation("org.slf4j:slf4j-api:1.7.9")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-properties:1.2.1")
    //----END OF KTOR DEPENDENCIES

    //----DATABASE DEPENDENCIES
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jodatime:$exposedVersion")
    implementation("org.postgresql:postgresql:42.2.2")
    //----END OF DATABASE DEPENDENCIES

    //----OTHER DEPENDENCIES
    implementation("it.skrape:skrapeit-core:1.0.0-alpha8")
    implementation("com.auth0:java-jwt:3.16.0")
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.0.4")
    implementation("com.github.ajalt.mordant:mordant:2.0.0-beta2")
    implementation("team.yi.ktor:ktor-banner:0.2.0")
    implementation("org.docx4j:docx4j-core:11.2.9") {
        exclude("log4j")
        exclude("org.slf4j")
    }
    implementation("org.docx4j:docx4j-openxml-objects:11.2.9"){
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
        props["appVersion"] = getGitVersion()
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
    archiveClassifier.set("uber")
}