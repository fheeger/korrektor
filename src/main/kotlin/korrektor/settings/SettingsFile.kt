package korrektor.settings

import korrektor.format.AtomicFile
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * The settings file itself, without any meaning of its content (that is [AppSettings]): reads it forgivingly, moves a
 * file that cannot be used aside as `settings.json.bad`, writes it atomically, and never lets a problem stop the app.
 * With [file] null nothing is read or written.
 */
internal class SettingsFile(
    private val file: Path?,
) {
    /** False if the file exists but could not be read: then nothing is written, or other settings would go. */
    private var readable = true

    /** How many times the file was written; tests use it to see that nothing is written for no change. */
    @Volatile
    var writeCount: Int = 0
        private set

    /** The settings in the file; empty if there is none, it is empty, or it cannot be used (then it is set aside). */
    fun read(): JsonObject {
        val path = file
        val text = path?.takeIf { it.exists() }?.let(::readText)
        return when {
            text == null || text.isBlank() -> JsonObject(emptyMap())
            else -> SettingsJson.parse(text) ?: JsonObject(emptyMap()).also { setAside(path) }
        }
    }

    /** Writes [settings] to the file. A failure is reported on stderr and is not an error for the caller. */
    fun write(settings: JsonObject) {
        val path = file?.takeIf { readable } ?: return
        try {
            Files.createDirectories(path.parent)
            AtomicFile.write(path, SettingsJson.encode(settings).toByteArray(Charsets.UTF_8))
            writeCount++
        } catch (e: IOException) {
            warn("The settings could not be saved to $path: ${e.message}")
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
}
