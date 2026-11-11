plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.2"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    testImplementation(kotlin("test"))
    // Only to read page sizes of the sample PDF in tests (T008). PDF rendering uses PDFium (D02).
    testImplementation("org.apache.pdfbox:pdfbox:3.0.8")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "korrektor.MainKt"
    }
}
