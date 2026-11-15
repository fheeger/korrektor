package korrektor.settings

import korrektor.format.AtomicFile
import korrektor.viewer.SavedView
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText

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
    private val versionEntry = SettingsJson.VERSION to JsonPrimitive(SettingsJson.VERSION_NUMBER)
    private var root: JsonObject? = null

    /** False if the file exists but cannot be read: then nothing is written, or other projects' views would go. */
    private var readable = true

    /** How many times the file was written; tests use it to see that nothing is written for no change. */
    @Volatile
    var writeCount: Int = 0
        private set

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
            root =
                JsonObject(current() + (SettingsJson.PROJECTS to JsonObject(updated)) + versionEntry)
            write()
        }
    }

    private fun key(folder: Path): String = folder.toAbsolutePath().normalize().toString()

    private fun projects(): JsonObject = current()[SettingsJson.PROJECTS] as? JsonObject ?: EMPTY

    private fun current(): JsonObject = root ?: load().also { root = it }

    // --- the file -------------------------------------------------------------------------------------------

    private fun load(): JsonObject {
        val path = file
        val text = path?.takeIf { it.exists() }?.let(::readText)
        return when {
            text == null || text.isBlank() -> EMPTY
            else -> SettingsJson.parse(text) ?: EMPTY.also { setAside(path) }
        }
    }

    private fun readText(path: Path): String? =
        try {
            path.readText()
        } catch (e: IOException) {
            warn("The settings in $path cannot be read, they are kept in memory only: ${e.message}")
            readable = false
            null
        }

    private fun write() {
        val path = file?.takeIf { readable } ?: return
        try {
            Files.createDirectories(path.parent)
            AtomicFile.write(path, SettingsJson.encode(current()).toByteArray(Charsets.UTF_8))
            writeCount++
        } catch (e: IOException) {
            warn("The settings could not be saved to $path: ${e.message}")
        }
    }

    private fun setAside(path: Path) {
        val bad = path.resolveSibling("${path.name}.bad")
        try {
            Files.move(path, bad, StandardCopyOption.REPLACE_EXISTING)
            warn("The settings in $path cannot be used and were moved to $bad")
        } catch (e: IOException) {
            warn("The settings in $path cannot be used and could not be moved aside: ${e.message}")
        }
    }

    private fun warn(message: String) = System.err.println("korrektor: $message")

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
