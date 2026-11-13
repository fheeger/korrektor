package korrektor.format

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Typed access to the JSON of a page file. Every failure is a FormatException that names the place in the file.

internal fun bad(
    path: String,
    expected: String,
): Nothing = throw FormatException("Page file: $path must be $expected")

internal fun JsonElement.asObject(path: String): JsonObject = this as? JsonObject ?: bad(path, "an object")

internal fun JsonElement.asString(path: String): String =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: bad(path, "a string")

/** The member if it is present and not null. */
internal fun JsonObject.optional(key: String): JsonElement? = this[key]?.takeIf { it !is JsonNull }

internal fun JsonObject.field(
    key: String,
    path: String,
): JsonElement = optional(key) ?: throw FormatException("Page file: $path is missing \"$key\"")

internal fun JsonObject.string(
    key: String,
    path: String,
) = field(key, path).asString("$path.$key")

internal fun JsonObject.array(
    key: String,
    path: String,
) = field(key, path) as? JsonArray ?: bad("$path.$key", "an array")

internal fun JsonObject.extra(known: Set<String>): Map<String, JsonElement> = filterKeys { it !in known }
