package korrektor.format

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageCodecTest {
    private fun resource(name: String) = javaClass.getResource("/format/$name")!!.readText(Charsets.UTF_8)

    private fun w(
        text: String,
        x: Double? = null,
    ) = Word(text, x?.let { Box(it, 10.0, 20.5, 12.0) })

    private fun samplePage() =
        PageLayer(
            pdfPage = 7,
            width = 595.3,
            height = 841.9,
            regions =
                listOf(
                    Region(
                        "r1",
                        RegionType.TEXT,
                        1,
                        Box(1.0, 2.0, 3.0, 4.0),
                        listOf(
                            Line(
                                "l1",
                                Box(1.0, 2.0, 3.0, 4.0),
                                "l1",
                                listOf(w("ſo", 1.0), w("uͤber", 2.0), w("ꝛc.", 3.0), w("\"q\\\"", 4.0), w("ü", 5.0)),
                            ),
                            Line("l2", words = listOf(w("neu"), w("typed"))),
                        ),
                    ),
                    Region("r2", RegionType("marginalia"), 2, lines = emptyList()),
                ),
        )

    private fun extraPage() =
        PageLayer(
            1,
            100.0,
            200.0,
            listOf(
                Region(
                    "r1",
                    RegionType.TEXT,
                    1,
                    lines =
                        listOf(
                            Line(
                                "l1",
                                words =
                                    listOf(
                                        Word("ſo", Box(1.0, 2.0, 3.0, 4.0), mapOf("conf" to JsonPrimitive(0.93))),
                                        Word("ꝛc."),
                                    ),
                                extra = mapOf("tag" to JsonObject(mapOf("a" to CanonicalJson.parse("[1, 2]"), "b" to JsonNull))),
                            ),
                        ),
                    extra = mapOf("style" to JsonPrimitive("bold")),
                ),
            ),
            mapOf("scanner" to JsonPrimitive("x")),
        )

    @Test
    fun decodeOfEncodeGivesTheSamePage() {
        for (page in listOf(samplePage(), extraPage())) {
            assertEquals(page, PageCodec.decode(PageCodec.encode(page)))
        }
    }

    @Test
    fun writerOutputIsStable() {
        for (page in listOf(samplePage(), extraPage())) {
            val text = PageCodec.encode(page)
            assertEquals(text, PageCodec.encode(PageCodec.decode(text)))
        }
    }

    @Test
    fun canonicalFixturesRoundTripByteForByte() {
        for (name in listOf("page-canonical.json", "page-unknown-fields.json")) {
            val text = resource(name)
            assertEquals(text, PageCodec.encode(PageCodec.decode(text)), name)
        }
    }

    @Test
    fun fixtureContentIsDecodedAsExpected() {
        val page = PageCodec.decode(resource("page-canonical.json"))
        assertEquals(3, page.pdfPage)
        assertEquals("Abhandlung über", page.regions[0].lines[0].text)
        assertNull(page.regions[0].lines[1].box)
        assertNull(page.regions[0].lines[1].origin)
        assertEquals("marginalia", page.regions[1].type.name)
        val unknown = PageCodec.decode(resource("page-unknown-fields.json"))
        assertEquals(extraPage(), unknown)
    }

    @Test
    fun changingOneWordChangesExactlyOneLine() {
        val page = samplePage()
        val region = page.regions[0]
        val changed =
            page.copy(
                regions =
                    listOf(
                        region.copy(lines = listOf(region.lines[0], region.lines[1].copy(words = listOf(w("neu"), w("fixed"))))),
                        page.regions[1],
                    ),
            )
        val before = PageCodec.encode(page).lines()
        val after = PageCodec.encode(changed).lines()
        assertEquals(before.size, after.size)
        assertEquals(1, before.indices.count { before[it] != after[it] })
    }

    @Test
    fun aLongLineIsOneLineInTheFile() {
        val words = (1..20).map { w("word$it", it.toDouble()) }
        val page = PageLayer(1, 100.0, 100.0, listOf(Region("r1", RegionType.TEXT, 1, lines = listOf(Line("l1", words = words)))))
        val lines = PageCodec.encode(page).lines()
        assertEquals(1, lines.count { it.contains("\"l1\"") && it.contains("word20") })
    }

    @Test
    fun coordinatesAreStoredWithOneDecimal() {
        val page = PageLayer(1, 100.04, 200.0, listOf(Region("r1", RegionType.TEXT, 1, Box(12.04, -0.04, 1.0, 2.0), emptyList())))
        val text = PageCodec.encode(page)
        assertTrue("\"width\": 100.0," in text)
        assertTrue("\"box\": [12.0, 0.0, 1.0, 2.0]" in text)
        // Reading rounds the same way, so decoding a hand-written file gives what would be written back.
        assertEquals(
            12.0,
            PageCodec
                .decode(text.replace("12.0", "12.04"))
                .regions[0]
                .box!!
                .x,
        )
    }

    @Test
    fun nullOptionalFieldsAreAccepted() {
        val text = """{"pdfPage": 1, "width": 1.0, "height": 1.0, "regions": [
            {"id": "r1", "type": "text", "order": 1, "box": null, "lines": [{"id": "l1", "box": null, "origin": null, "words": []}]}]}"""
        val line = PageCodec.decode(text).regions[0].lines[0]
        assertNull(line.box)
        assertNull(line.origin)
    }

    private fun assertInvalid(
        text: String,
        part: String,
    ) {
        val e = assertFailsWith<FormatException> { PageCodec.decode(text) }
        assertTrue(part in e.message!!, "Expected '$part' in: ${e.message}")
    }

    @Test
    fun invalidFilesAreRejectedWithTheWrongPart() {
        val head = """"pdfPage": 1, "width": 1.0, "height": 1.0"""
        assertInvalid("""{"width": 1.0, "height": 1.0, "regions": []}""", "pdfPage")
        assertInvalid("""{"pdfPage": "1", "width": 1.0, "height": 1.0, "regions": []}""", "pdfPage")
        assertInvalid("""{$head, "regions": {}}""", "regions")
        assertInvalid(
            """{$head, "regions": [{"id": "r1", "type": "text", "order": 1, "lines": [{"id": "l1", "words": [["a", 1]]}]}]}""",
            "regions[0].lines[0].words[0]",
        )
        assertInvalid("""{$head, "regions": [{"id": "r1", "type": "text", "order": 1, "box": [1, 2, 3], "lines": []}]}""", "regions[0].box")
        assertInvalid(
            """{$head, "regions": [{"id": "r1", "type": "text", "order": 1, "lines": [{"id": "l1", "words": [[5]]}]}]}""",
            "words[0][0]",
        )
        assertInvalid("""{"pdfPage": 1, "width": 0, "height": 1.0, "regions": []}""", "size")
        assertInvalid(
            """{$head, "regions": [{"id": "r1", "type": "text", "order": 1, "lines": [{"id": "l1", "words": []}, {"id": "l1", "words": []}]}]}""",
            "l1",
        )
        assertInvalid("""[1]""", "object")
        assertInvalid("""{"pdfPage": """, "Invalid JSON")
    }
}
