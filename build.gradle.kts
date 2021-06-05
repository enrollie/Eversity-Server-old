import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktorVersion: String by project
val kotlinVersion: String by project
val logbackVersion: String by project
val exposedVersion: String by project
val multikVersion: String by project

plugins {
    application
    kotlin("jvm") version "1.5.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.5.0"
    id("com.github.johnrengelman.shadow") version "6.1.0"
}

group = "by.enrollie.educad"
version = "0.0.1"
application {
    mainClass.set("io.ktor.server.netty.EngineMain")
    mainClassName = "io.ktor.server.netty.EngineMain"
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    //----KTOR DEPENDENCIES
    implementation("io.ktor:ktor-server-core:$ktorVersion")
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
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    //----END OF KTOR DEPENDENCIES

    //----DATABASE DEPENDENCIES
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.postgresql:postgresql:42.2.2")
    implementation("org.jetbrains.exposed:exposed-jodatime:$exposedVersion")
    //----END OF DATABASE DEPENDENCIES

    //----OTHER DEPENDENCIES
    implementation("it.skrape:skrapeit-core:1.0.0-alpha8")
    implementation("com.auth0:java-jwt:3.16.0")
    implementation("org.jetbrains.kotlinx:multik-api:$multikVersion")
    implementation(files("libs/ktor-banner.jar"))
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
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

tasks.register<Jar>("uberJar") {
    archiveClassifier.set("uber")

    manifest {
        attributes(
            "Main-Class" to "io.ktor.server.netty.EngineMain",
            "Implementation-Title" to "Gradle",
            "Implementation-Version" to archiveVersion
        )
    }

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}