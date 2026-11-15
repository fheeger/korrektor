package korrektor.settings

import korrektor.viewer.SavedView
import korrektor.viewer.SavedZoom
import korrektor.viewer.ZoomMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The shape of the settings file (see [AppSettings]) as functions on JSON, without any file. Reading is forgiving:
 * whatever is not as expected is "not there". Writing builds on the old entry, so fields it does not know stay.
 */
internal object SettingsJson {
    const val VERSION = "version"
    const val PROJECTS = "projects"
    const val VERSION_NUMBER = 1
    private const val PAGE = "page"
    private const val ZOOM = "zoom"
    private const val MODE = "mode"
    private const val SCALE = "scale"
    private val MODE_NAMES =
        mapOf(ZoomMode.FIT_WIDTH to "fitWidth", ZoomMode.FIT_PAGE to "fitPage", ZoomMode.FIXED to "fixed")
    private val PRETTY = Json { prettyPrint = true }

    /** The settings in [text], or null if it is not JSON of the right shape or is from a newer version. */
    fun parse(text: String): JsonObject? {
        val parsed = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
        val version = (parsed?.get(VERSION) as? JsonPrimitive)?.intOrNull
        val projects = parsed?.get(PROJECTS)
        val usable = version != null && version <= VERSION_NUMBER && (projects == null || projects is JsonObject)
        return parsed?.takeIf { usable }
    }

    fun encode(settings: JsonObject): String = PRETTY.encodeToString(JsonObject.serializer(), settings)

    /** The view in a project's entry, or null if the entry is not valid. */
    fun readView(entry: JsonObject): SavedView? {
        val page = number(entry[PAGE])?.intOrNull
        val zoom = readZoom(entry[ZOOM] as? JsonObject)
        return if (page != null && zoom != null) SavedView(page, zoom) else null
    }

    private fun readZoom(zoom: JsonObject?): SavedZoom? {
        val mode =
            MODE_NAMES.entries
                .firstOrNull {
                    it.value ==
                        (
                            zoom?.get(
                                MODE,
                            ) as? JsonPrimitive
                        )?.contentOrNull
                }?.key
        val scale = number(zoom?.get(SCALE))?.doubleOrNull?.takeIf { it > 0 && it.isFinite() }
        return when (mode) {
            null -> null
            ZoomMode.FIXED -> scale?.let { SavedZoom(mode, it) }
            else -> SavedZoom(mode)
        }
    }

    /** A JSON number, as opposed to text that looks like one. */
    private fun number(element: JsonElement?): JsonPrimitive? = (element as? JsonPrimitive)?.takeIf { !it.isString }

    /** [view] as an entry, on top of the [old] one so that fields this version does not know stay. */
    fun writeView(
        old: JsonObject?,
        view: SavedView,
    ): JsonObject {
        val zoom = LinkedHashMap<String, JsonElement>((old?.get(ZOOM) as? JsonObject) ?: emptyMap())
        zoom[MODE] = JsonPrimitive(MODE_NAMES.getValue(view.zoom.mode))
        val scale = view.zoom.scale
        if (scale != null) zoom[SCALE] = JsonPrimitive(scale) else zoom.remove(SCALE)
        return JsonObject((old ?: emptyMap()) + (PAGE to JsonPrimitive(view.page)) + (ZOOM to JsonObject(zoom)))
    }
}
