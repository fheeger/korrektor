package korrektor.settings

import korrektor.export.ExportFormat
import korrektor.export.ExportOptions
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

// What the app settings remember of the export (S11): the options of the last export of each format, under `export`.
// Extensions, because `AppSettings` has as many functions as the code checks allow.

private const val EXPORT = "export"
private const val PAGE_MARKERS = "pageMarkers"
private const val HEADER = "header"
private const val KEEP_LINE_BREAKS = "keepLineBreaks"
private val EMPTY = JsonObject(emptyMap())
private val FORMAT_NAMES = mapOf(ExportFormat.TEXT to "text", ExportFormat.MARKDOWN to "markdown")

/** The options of the last export of [format], or its defaults if there are none or they are not valid. */
fun AppSettings.exportOptions(format: ExportFormat): ExportOptions =
    synchronized(lock) { readExport(exportSection(), format) }

/** Remembers [options] as those of their format and writes the file (unless they are what is remembered already). */
fun AppSettings.rememberExportOptions(options: ExportOptions) {
    synchronized(lock) {
        val updated = writeExport(exportSection(), options)
        if (updated != exportSection()) save(JsonObject(current() + (EXPORT to updated)))
    }
}

private fun AppSettings.exportSection(): JsonObject = current()[EXPORT] as? JsonObject ?: EMPTY

private fun readExport(
    export: JsonObject,
    format: ExportFormat,
): ExportOptions {
    val defaults = ExportOptions.defaultsFor(format)
    val entry = export[FORMAT_NAMES.getValue(format)] as? JsonObject ?: return defaults
    return defaults.copy(
        pageMarkers = flag(entry[PAGE_MARKERS]) ?: defaults.pageMarkers,
        header = flag(entry[HEADER]) ?: defaults.header,
        keepLineBreaks = flag(entry[KEEP_LINE_BREAKS]) ?: defaults.keepLineBreaks,
    )
}

/** The `export` object with [options] for their format; the other format and fields this version does not know stay. */
private fun writeExport(
    export: JsonObject,
    options: ExportOptions,
): JsonObject {
    val key = FORMAT_NAMES.getValue(options.format)
    val entry =
        JsonObject(
            (export[key] as? JsonObject ?: emptyMap()) +
                (PAGE_MARKERS to JsonPrimitive(options.pageMarkers)) +
                (HEADER to JsonPrimitive(options.header)) +
                (KEEP_LINE_BREAKS to JsonPrimitive(options.keepLineBreaks)),
        )
    return JsonObject(export + (key to entry))
}

/** A JSON true or false, as opposed to text that looks like one. */
private fun flag(element: JsonElement?): Boolean? = (element as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
