package korrektor.format

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ModelTest {
    private fun word(
        text: String,
        boxed: Boolean = true,
    ) = Word(text, if (boxed) Box(10.0, 20.0, 30.0, 12.0) else null)

    private fun page(vararg regions: Region) = PageLayer(pdfPage = 1, width = 595.3, height = 841.9, regions = regions.toList())

    private fun region(
        id: String,
        order: Int,
        vararg lines: Line,
    ) = Region(id, RegionType.TEXT, order, lines = lines.toList())

    @Test
    fun buildsPageWithBoxedAndUnboxedLines() {
        val original = Line("l1", Box(10.0, 20.0, 100.0, 12.0), "l1", listOf(word("Abhandlung"), word("über")))
        val typed = Line("l2", words = listOf(word("neu", boxed = false)))
        val p = page(region("r1", 1, original, typed))

        assertNull(p.regions[0].lines[1].box)
        assertNull(p.regions[0].lines[1].origin)
        assertNull(
            p.regions[0]
                .lines[1]
                .words[0]
                .box,
        )
    }

    @Test
    fun lineTextJoinsWordsAndKeepsSpecialCharacters() {
        val line = Line("l1", words = listOf(word("ſo"), word("uͤber"), word("ꝛc."), word("ü")))
        assertEquals("ſo uͤber ꝛc. ü", line.text)
    }

    @Test
    fun rejectsDuplicateLineIds() {
        val a = Line("l1", words = listOf(word("a")))
        val e = assertFailsWith<IllegalArgumentException> { page(region("r1", 1, a), region("r2", 2, a)) }
        assertEquals("Duplicate line ID 'l1' on page 1", e.message)
    }

    @Test
    fun rejectsDuplicateRegionIdsAndOrders() {
        assertFailsWith<IllegalArgumentException> { page(region("r1", 1), region("r1", 2)) }
        val e = assertFailsWith<IllegalArgumentException> { page(region("r1", 1), region("r2", 1)) }
        assertEquals("Duplicate region order '1' on page 1", e.message)
    }

    @Test
    fun rejectsInvalidPageSizeAndNumber() {
        assertFailsWith<IllegalArgumentException> { PageLayer(1, 0.0, 10.0, emptyList()) }
        assertFailsWith<IllegalArgumentException> { PageLayer(1, 10.0, -1.0, emptyList()) }
        assertFailsWith<IllegalArgumentException> { PageLayer(0, 10.0, 10.0, emptyList()) }
    }

    @Test
    fun rejectsBlankIdsAndOrigin() {
        assertFailsWith<IllegalArgumentException> { Line("", words = emptyList()) }
        assertFailsWith<IllegalArgumentException> { Line("l1", origin = " ", words = emptyList()) }
        assertFailsWith<IllegalArgumentException> { Region(" ", RegionType.TEXT, 1, lines = emptyList()) }
    }

    @Test
    fun unknownFieldsTakePartInEquality() {
        val plain = Word("a")
        val withExtra = Word("a", extra = mapOf("conf" to JsonPrimitive(0.9)))
        assertNotEquals(plain, withExtra)
        assertEquals(withExtra, Word("a", extra = mapOf("conf" to JsonPrimitive(0.9))))
    }

    @Test
    fun unknownRegionTypesAreKept() {
        assertEquals("marginalia", Region("r1", RegionType("marginalia"), 1, lines = emptyList()).type.name)
    }
}
