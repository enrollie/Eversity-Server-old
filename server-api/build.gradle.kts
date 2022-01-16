/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "by.enrollie.eversity"
version = parent?.version.toString()

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("joda-time:joda-time:2.10.13")
}
val compileKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "11"
}

val compileTestKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "11"
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            pom {
                name.set("Eversity Plugin API")
                description.set("API used to create plugins for Eversity")
                url.set("https://github.com/enrollie/Eversity-Server")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("pavelm")
                        name.set("Pavel Matusevich")
                        email.set("neitex@protonmail.com")
                        url.set("https://neitex.me")
                        roles.add("Main developer")
                        timezone.set("GMT+3")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/enrollie/Eversity-Server.git")
                    url.set("https://github.com/enrollie/Eversity-Server.git")
                }
                versionMapping {
                    usage("java-api") {
                        fromResolutionOf("runtimeClasspath")
                    }
                    usage("java-runtime") {
                        fromResolutionResult()
                    }
                }
            }
            repositories {
                maven {
                    url = uri("https://packages.neitex.me/releases")
                    credentials.username = System.getenv("reposiliteAlias")
                    credentials.password = System.getenv("reposilitePassword")

                    authentication {
                        create<BasicAuthentication>("basic")
                    }
                }
            }
            groupId = "by.enrollie"
            artifactId = "eversity_api"
            version = "2"

            from(components["java"])
        }
    }
}
