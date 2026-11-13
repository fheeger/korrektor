import java.net.URI
import java.security.MessageDigest

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
    // Access to PDFium's native library (D04).
    implementation("net.java.dev.jna:jna:5.19.1")
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

// PDFium (D02, D04): the prebuilt library of bblanchon/pdfium-binaries is downloaded once, checked against a
// pinned SHA-256 and unpacked into build/pdfium. It is not committed (about 4 MB per platform).
val pdfiumRelease = "chromium/8152"
val pdfiumSha256 =
    mapOf(
        "win-x64" to "cee2b0e9990bc67c0721774932961b9dbf2766046c10eec925acd4cfba6fdfe3",
        "linux-x64" to "384ab5c51882408a143a21f9c14dcd2caf1f75280ae41c554d0489b46c3dba9f",
        "mac-x64" to "acb975e637ffa1f41430205c62baacac70e34b60811e22fc0e0274dfcd601fcc",
        "mac-arm64" to "30b0c3f55448840f0947b54bedbe35185bbaccbadd35b8cd960270d6fa6caf80",
    )
val pdfiumDir = layout.buildDirectory.dir("pdfium")

val downloadPdfium =
    tasks.register("downloadPdfium") {
        val osName = System.getProperty("os.name").lowercase()
        val arm = System.getProperty("os.arch").lowercase().let { it.contains("aarch64") || it.contains("arm64") }
        val platform =
            when {
                osName.contains("win") -> "win-x64"
                osName.contains("mac") -> if (arm) "mac-arm64" else "mac-x64"
                else -> "linux-x64"
            }
        val outDir = pdfiumDir
        inputs.property("release", pdfiumRelease)
        inputs.property("platform", platform)
        outputs.dir(outDir)
        doLast {
            val expected = pdfiumSha256.getValue(platform)
            val base = "https://github.com/bblanchon/pdfium-binaries/releases/download"
            val url = "$base/$pdfiumRelease/pdfium-$platform.tgz"
            val target = outDir.get().asFile
            target.mkdirs()
            val archive = File(target, "pdfium-$platform.tgz")
            logger.lifecycle("Downloading PDFium $pdfiumRelease for $platform")
            URI(url).toURL().openStream().use { input -> archive.outputStream().use { input.copyTo(it) } }
            val actual =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(archive.readBytes())
                    .joinToString("") { "%02x".format(it) }
            if (actual != expected) {
                archive.delete()
                throw GradleException(
                    "PDFium download from $url has the wrong SHA-256: expected $expected but was $actual",
                )
            }
            // The library lands directly in build/pdfium; the license files in build/pdfium/licenses.
            copy {
                from(tarTree(resources.gzip(archive)))
                include("bin/*.dll", "lib/*.so", "lib/*.dylib")
                eachFile { relativePath = RelativePath(true, name) }
                includeEmptyDirs = false
                into(target)
            }
            copy {
                from(tarTree(resources.gzip(archive)))
                include("LICENSE", "licenses/**")
                into(target)
            }
            archive.delete()
        }
    }

tasks.test {
    useJUnitPlatform()
    dependsOn(downloadPdfium)
    systemProperty("jna.library.path", pdfiumDir.get().asFile.absolutePath)
}

tasks.withType<JavaExec>().configureEach {
    dependsOn(downloadPdfium)
    systemProperty("jna.library.path", pdfiumDir.get().asFile.absolutePath)
}

compose.desktop {
    application {
        mainClass = "korrektor.MainKt"
    }
}
