package korrektor

import java.util.Properties

/** What the app knows about itself: the version comes from `build.gradle.kts` through `app.properties`. */
object AppInfo {
    /** The address of the source and the issue tracker. */
    const val REPOSITORY = "https://github.com/fheeger/korrektor"

    /** The license of the app. */
    const val LICENSE = "GPL-3.0"

    /** The version of the app, or "unknown" if the resource is missing (a run outside the build). */
    val version: String by lazy {
        val properties = Properties()
        AppInfo::class.java.getResourceAsStream("/app.properties")?.use { properties.load(it) }
        properties.getProperty("version")?.takeUnless { it.startsWith("$") } ?: "unknown"
    }
}
