@file:Suppress("PropertyName", "VulnerableLibrariesLocal")

val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val openapi_version: String by project
val hapi_version: String by project
val config4k_version: String by project

plugins {
    application
    kotlin("jvm") version "1.7.20"
}

group = "de.uniluebeck.itcr.highmed"
version = "0.0.1"

application {
    mainClass.set("io.ktor.server.cio.EngineMain")
}

repositories {
    mavenLocal()
    //jcenter()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("io.ktor:ktor-server-cio:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-host-common:$ktor_version")
    implementation("io.ktor:ktor-gson:$ktor_version")
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-json-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-gson:$ktor_version")
    implementation("io.ktor:ktor-client-logging-jvm:$ktor_version")
    testImplementation("io.ktor:ktor-server-tests:$ktor_version")
    implementation("io.ktor:ktor-client-auth:$ktor_version")
    implementation("com.github.papsign:Ktor-OpenAPI-Generator:$openapi_version")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.14.0")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-base:$hapi_version")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-structures-r4:$hapi_version")
    implementation("io.github.config4k:config4k:$config4k_version")
    implementation("org.apache.commons:commons-text:1.10.0")
}

kotlin.sourceSets["main"].kotlin.srcDirs("src")
kotlin.sourceSets["test"].kotlin.srcDirs("test")

sourceSets["main"].resources.srcDirs("resources")
sourceSets["test"].resources.srcDirs("resources")