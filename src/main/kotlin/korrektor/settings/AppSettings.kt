package korrektor.settings

import korrektor.viewer.SavedView
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path

/**
 * The settings of the app, as opposed to those of a project: for now the page and zoom last viewed in each project.
 * They live in one JSON file in the user's config folder and never in a project, whose format stays as it is (V3):
 *
 * ```
 * {"version": 1, "projects": {"<project folder>": {"page": 12, "zoom": {"mode": "fixed", "scale": 1.5}}}}
 * ```
 *
 * The settings are a convenience, so nothing here may stop the app. A missing or empty file means no settings; a file
 * that is damaged, or written by a newer version of the app, is moved aside as `settings.json.bad` (the newer
 * version's data is not overwritten by an older one) and the app starts with none. A write that fails is reported on
 * stderr and the settings stay in memory. Fields this version does not know are kept when the file is written again.
 * At most [maxProjects] projects are kept, the ones used least recently go first. A project is known by the path of
 * its folder, so a project that was moved starts afresh.
 *
 * With [file] null the settings live in memory only, which is what tests and a failed start use.
 */
class AppSettings(
    private val file: Path?,
    private val maxProjects: Int = MAX_PROJECTS,
) {
    private val lock = Any()
    private val store = SettingsFile(file)
    private val versionEntry = SettingsJson.VERSION to JsonPrimitive(SettingsJson.VERSION_NUMBER)
    private var root: JsonObject? = null

    /** How many times the file was written; tests use it to see that nothing is written for no change. */
    val writeCount: Int get() = store.writeCount

    /** The saved view of the project in [folder], or null if there is none or it is not valid. */
    fun viewOf(folder: Path): SavedView? =
        synchronized(lock) { (projects()[key(folder)] as? JsonObject)?.let(SettingsJson::readView) }

    /** Remembers [view] for the project in [folder] and writes the file (unless nothing changed). */
    fun rememberView(
        folder: Path,
        view: SavedView,
    ) {
        synchronized(lock) {
            val key = key(folder)
            val projects = projects()
            val old = projects[key] as? JsonObject
            val entry = SettingsJson.writeView(old, view)
            if (entry == old) return
            // The entry moves to the end, so that the oldest are at the front when there are too many.
            val updated = LinkedHashMap(projects)
            updated.remove(key)
            updated[key] = entry
            while (updated.size > maxProjects) updated.remove(updated.keys.first())
            save(JsonObject(current() + (SettingsJson.PROJECTS to JsonObject(updated))))
        }
    }

    /** The saved position of the divider between scan and text (a fraction of the width), if it is a number. */
    fun splitFraction(): Double? = synchronized(lock) { SettingsJson.readSplit(ui()) }

    /** Remembers the position of the divider and writes the file (unless it is the same as before). */
    fun rememberSplitFraction(fraction: Double) {
        synchronized(lock) {
            val updated = SettingsJson.writeSplit(ui(), fraction)
            if (updated != ui()) save(JsonObject(current() + (SettingsJson.UI to updated)))
        }
    }

    private fun ui(): JsonObject = current()[SettingsJson.UI] as? JsonObject ?: EMPTY

    private fun save(settings: JsonObject) {
        root = JsonObject(settings + versionEntry)
        store.write(root!!)
    }

    private fun key(folder: Path): String = folder.toAbsolutePath().normalize().toString()

    private fun projects(): JsonObject = current()[SettingsJson.PROJECTS] as? JsonObject ?: EMPTY

    private fun current(): JsonObject = root ?: store.read().also { root = it }

    companion object {
        const val MAX_PROJECTS = 200
        private val EMPTY = JsonObject(emptyMap())

        private fun xdgConfig(env: (String) -> String?): Path? =
            env("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() && Path.of(it).isAbsolute }?.let { Path.of(it) }

        /** Settings that are not written anywhere. */
        fun inMemory() = AppSettings(null)

        /**
         * The settings file in the user's config folder: `%APPDATA%\korrektor` on Windows, `Library/Application
         * Support` on macOS, `$XDG_CONFIG_HOME` or `~/.config` elsewhere. The parameters are seams for tests.
         */
        fun defaultFile(
            osName: String = System.getProperty("os.name"),
            home: String = System.getProperty("user.home"),
            env: (String) -> String? = System::getenv,
        ): Path {
            val os = osName.lowercase()
            val base =
                when {
                    os.contains("win") -> Path.of(env("APPDATA")?.takeIf { it.isNotBlank() } ?: "$home/AppData/Roaming")
                    os.contains("mac") -> Path.of(home, "Library", "Application Support")
                    else -> xdgConfig(env) ?: Path.of(home, ".config")
                }
            return base.resolve("korrektor").resolve("settings.json")
        }
    }
}
