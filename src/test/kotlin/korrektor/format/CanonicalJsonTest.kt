package korrektor.format

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CanonicalJsonTest {
    @Test
    fun numbersHaveExactlyOneDecimal() {
        assertEquals("12.0", CanonicalJson.formatNumber(12.0))
        assertEquals("12.0", CanonicalJson.formatNumber(12.04))
        assertEquals("12.1", CanonicalJson.formatNumber(12.05))
        assertEquals("0.0", CanonicalJson.formatNumber(-0.04))
        assertEquals("-3.5", CanonicalJson.formatNumber(-3.5))
        assertEquals("1234567.9", CanonicalJson.formatNumber(1234567.89))
    }

    @Test
    fun onlyRequiredEscapesAreWritten() {
        assertEquals("\"a\\\"b\\\\c\\n\\t\"", CanonicalJson.quote("a\"b\\c\n\t"))
        assertEquals("\"\\u0001\"", CanonicalJson.quote("\u0001"))
        // Non-ASCII text, including combining characters, is written as it is.
        assertEquals("\"ſo u\u0364ber ꝛc. \uD83D\uDE00 /\"", CanonicalJson.quote("ſo u\u0364ber ꝛc. \uD83D\uDE00 /"))
    }

    @Test
    fun unpairedSurrogateIsRejected() {
        assertFailsWith<FormatException> { CanonicalJson.quote("a\uD800b") }
        assertFailsWith<FormatException> { CanonicalJson.quote("\uDC00") }
    }

    @Test
    fun objectsAreWrittenCompactWithSortedKeysAndRawNumbers() {
        val element = CanonicalJson.parse("""{ "b": [1, 2.50, "x"], "a": null, "c": {"z": true, "y": 1e3} }""")
        assertEquals("""{"a": null, "b": [1, 2.50, "x"], "c": {"y": 1e3, "z": true}}""", CanonicalJson.write(element))
        assertEquals("null", CanonicalJson.write(JsonNull))
        assertEquals("\"1\"", CanonicalJson.write(JsonPrimitive("1")))
    }

    @Test
    fun invalidJsonIsAFormatException() {
        assertFailsWith<FormatException> { CanonicalJson.parse("{\"a\": ") }
    }
}
