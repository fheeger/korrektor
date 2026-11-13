plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.2"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("org.jetbrains.kotlinx.kover") version "0.9.11"
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    // Compose Multiplatform has no stable Material 3 newer than 1.9.0; it works with Compose 1.12 (see T011).
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    // The same version Compose brings along; declared because the dialog models use coroutines directly.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.compose.ui:ui-test:1.12.2")
    detektPlugins("io.nlopez.compose.rules:detekt:0.4.28")
    // Only to read page sizes of the sample PDF in tests (T008). PDF rendering uses PDFium (D02).
    testImplementation("org.apache.pdfbox:pdfbox:3.0.8")
}

kotlin {
    jvmToolchain(21)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("config/detekt/detekt.yml")
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "korrektor.MainKt"
    }
}
