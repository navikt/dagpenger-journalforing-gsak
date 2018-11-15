plugins {
    id("application")
    kotlin("jvm") version "1.2.70"
    id("com.diffplug.gradle.spotless") version "3.13.0"
    id("com.palantir.docker") version "0.20.1"
    id("com.palantir.git-version") version "0.11.0"
    id("com.adarshr.test-logger") version "1.5.0"
    id("java-library")
}

buildscript {
    repositories {
        maven("https://repo.adeo.no/repository/maven-central")
    }
}

apply {
    plugin("com.diffplug.gradle.spotless")
    plugin("com.adarshr.test-logger")
}

repositories {
    mavenCentral()
    maven("http://packages.confluent.io/maven")
    maven("https://dl.bintray.com/kotlin/ktor")
    maven("https://dl.bintray.com/kotlin/kotlinx")
    maven("https://dl.bintray.com/kittinunf/maven")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

val gitVersion: groovy.lang.Closure<Any> by extra
version = gitVersion()
group = "no.nav.dagpenger"

application {
    applicationName = "dagpenger-journalforing-gsak"
    mainClassName = "no.nav.dagpenger.journalføring.gsak.JournalføringGsak"
}

docker {
    name = "repo.adeo.no:5443/${application.applicationName}"
    buildArgs(
        mapOf(
            "APP_NAME" to application.applicationName,
            "DIST_TAR" to "${application.applicationName}-${project.version}"
        )
    )
    files(tasks.findByName("distTar")?.outputs)
    pull(true)
    tags(project.version.toString())
}

val kotlinLoggingVersion = "1.4.9"
val kafkaVersion = "2.0.0"
val confluentVersion = "4.1.2"
val ktorVersion = "0.9.5"
val prometheusVersion = "0.5.0"
val fuelVersion = "1.15.0"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("no.nav.dagpenger:streams:0.2.1-SNAPSHOT")
    implementation("no.nav.dagpenger:events:0.1.8-SNAPSHOT")

    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")

    implementation("com.github.kittinunf.fuel:fuel:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-gson:$fuelVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")

    api("org.apache.kafka:kafka-clients:$kafkaVersion")
    api("org.apache.kafka:kafka-streams:$kafkaVersion")
    api("io.confluent:kafka-streams-avro-serde:$confluentVersion")

    compile("io.ktor:ktor-server-netty:$ktorVersion")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.12")
    testImplementation("au.com.dius:pact-jvm-consumer-java8_2.12:3.6.0-rc.0")
}

spotless {
    kotlin {
        ktlint()
    }
    kotlinGradle {
        target("*.gradle.kts", "additionalScripts/*.gradle.kts")
        ktlint()
    }
}
