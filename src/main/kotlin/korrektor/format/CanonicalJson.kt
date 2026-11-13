package korrektor.format

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.readBytes
import kotlin.math.roundToLong

/**
 * Writing rules shared by all JSON files of a project, so that saving without changes gives identical bytes:
 * `, ` and `: ` as separators, object keys in alphabetical order, numbers as they were read (known coordinates
 * use [formatNumber]), and strings with only the escapes JSON requires. Text is never normalized (V1).
 */
object CanonicalJson {
    private const val DECIMAL_SHIFT = 10.0

    /** Compact single-line form of [element]. */
    fun write(element: JsonElement): String = StringBuilder().also { append(it, element) }.toString()

    /** Reads a file as UTF-8. Invalid bytes are an error, not silently replaced (that would change bytes on save). */
    fun readUtf8(file: Path): String =
        try {
            decodeUtf8(file.readBytes(), file)
        } catch (e: java.io.IOException) {
            throw FormatException("Cannot read $file: ${e.message}", e)
        }

    fun decodeUtf8(
        bytes: ByteArray,
        file: Path,
    ): String =
        try {
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            throw FormatException("${file.fileName} is not valid UTF-8", e)
        }

    fun parse(text: String): JsonElement =
        try {
            Json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw FormatException("Invalid JSON: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw FormatException("Invalid JSON: ${e.message}", e)
        }

    /** A coordinate with exactly one decimal, e.g. `12.0`. Rounds half up; never writes `-0.0`. */
    fun formatNumber(value: Double): String {
        require(value.isFinite()) { "Number must be finite, was $value" }
        return String.format(Locale.ROOT, "%.1f", round1(value)).let { if (it == "-0.0") "0.0" else it }
    }

    /** Rounds to the one decimal the format stores. */
    fun round1(value: Double): Double = (value * DECIMAL_SHIFT).roundToLong() / DECIMAL_SHIFT

    /** A JSON string literal. Only `"`, `\` and control characters are escaped. */
    fun quote(text: String): String = StringBuilder().also { appendQuoted(it, text) }.toString()

    private fun append(
        out: StringBuilder,
        element: JsonElement,
    ) {
        when (element) {
            is JsonNull -> out.append("null")
            is JsonPrimitive ->
                if (element.isString) {
                    appendQuoted(out, element.content)
                } else {
                    // NaN and Infinity are not JSON; writing them would make the file unreadable.
                    if (element.content in
                        setOf("NaN", "Infinity", "-Infinity")
                    ) {
                        throw FormatException("Not a JSON number: ${element.content}")
                    }
                    out.append(element.content)
                }
            is JsonArray -> {
                out.append('[')
                element.forEachIndexed { i, e ->
                    if (i > 0) out.append(", ")
                    append(out, e)
                }
                out.append(']')
            }
            is JsonObject -> {
                out.append('{')
                element.entries.sortedBy { it.key }.forEachIndexed { i, (k, v) ->
                    if (i > 0) out.append(", ")
                    appendQuoted(out, k)
                    out.append(": ")
                    append(out, v)
                }
                out.append('}')
            }
        }
    }

    private fun appendQuoted(
        out: StringBuilder,
        text: String,
    ) {
        out.append('"')
        for ((i, c) in text.withIndex()) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c == '\b' -> out.append("\\b")
                c == '\u000C' -> out.append("\\f")
                c < ' ' -> out.append(String.format(Locale.ROOT, "\\u%04x", c.code))
                // A lone surrogate can't be written as UTF-8; it would silently turn into '?'.
                Character.isSurrogate(c) && !isPaired(text, i) ->
                    throw FormatException("Text contains an unpaired surrogate at index $i")
                else -> out.append(c)
            }
        }
        out.append('"')
    }

    private fun isPaired(
        text: String,
        i: Int,
    ): Boolean =
        if (Character.isHighSurrogate(text[i])) {
            i + 1 < text.length && Character.isLowSurrogate(text[i + 1])
        } else {
            i > 0 && Character.isHighSurrogate(text[i - 1])
        }
}
