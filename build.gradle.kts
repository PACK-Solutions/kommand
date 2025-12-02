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
    testImplementation(kotlin("test"))
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
}
