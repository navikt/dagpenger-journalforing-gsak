
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("application")
    kotlin("jvm") version "1.3.21"
    id("com.diffplug.gradle.spotless") version "3.13.0"
    id("java-library")
    id("info.solidsoft.pitest") version "1.3.0"
    id("com.github.johnrengelman.shadow") version "4.0.3"
}

apply {
    plugin("com.diffplug.gradle.spotless")
}

repositories {
    jcenter()
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("http://packages.confluent.io/maven")
    maven("https://dl.bintray.com/kotlin/ktor")
    maven("https://dl.bintray.com/kotlin/kotlinx")
    maven("https://dl.bintray.com/kittinunf/maven")
    maven("https://jitpack.io")
}

application {
    applicationName = "dagpenger-journalforing-gsak"
    mainClassName = "no.nav.dagpenger.journalføring.gsak.JournalføringGsak"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

val kotlinLoggingVersion = "1.6.22"
val kafkaVersion = "2.0.1"
val confluentVersion = "4.1.2"
val ktorVersion = "1.0.0"
val prometheusVersion = "0.6.0"
val fuelVersion = "2.1.0"
val log4j2Version = "2.11.1"
val dpBibliotekerVersion = "2019.06.19-09.38.5466af242e44"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.github.navikt:dagpenger-streams:2019.06.21-06.51.ec8ab5f89d1a")
    implementation("com.github.navikt:dagpenger-events:2019.05.20-11.56.33cd4c73a439")

    implementation("com.github.navikt.dp-biblioteker:sts-klient:$dpBibliotekerVersion")

    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")

    implementation("com.github.kittinunf.fuel:fuel:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-gson:$fuelVersion")
    implementation("com.google.code.gson:gson:2.8.5")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")

    api("org.apache.kafka:kafka-clients:$kafkaVersion")
    api("org.apache.kafka:kafka-streams:$kafkaVersion")
    api("io.confluent:kafka-streams-avro-serde:$confluentVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")

    implementation("org.apache.logging.log4j:log4j-api:$log4j2Version")
    implementation("org.apache.logging.log4j:log4j-core:$log4j2Version")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4j2Version")
    implementation("com.vlkan.log4j2:log4j2-logstash-layout-fatjar:0.15")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.12")
    testImplementation("com.github.tomakehurst:wiremock:2.19.0")
    testImplementation("no.nav:kafka-embedded-env:2.0.2")
}

spotless {
    kotlin {
        ktlint("0.31.0")
    }
    kotlinGradle {
        target("*.gradle.kts", "additionalScripts/*.gradle.kts")
        ktlint("0.31.0")
    }
}

pitest {
    threads = 4
    coverageThreshold = 80
    pitestVersion = "1.4.3"
    avoidCallsTo = setOf("kotlin.jvm.internal")
    timestampedReports = false
    targetClasses = setOf("no.nav.dagpenger.*")
}

// tasks.getByName("check").finalizedBy("pitest")

tasks.withType<Test> {
    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}
