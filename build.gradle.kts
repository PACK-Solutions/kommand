plugins {
    kotlin("jvm") version "2.2.20"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "com.ps"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Kotest (unit testing)
    testImplementation("io.kotest:kotest-runner-junit5:6.0.7")
    testImplementation("io.kotest:kotest-assertions-core:6.0.7")

    // Coroutines (used across main and tests)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.michael-bull.kotlin-result:kotlin-result:2.1.0")
    // Detekt formatting plugin (must match Detekt plugin version)
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    autoCorrect = true
    // Ensure Gradle Detekt uses the repository's detekt.yml
    config.setFrom(rootProject.files("detekt.yml"))
}
