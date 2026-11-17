package korrektor.format

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The entries of the history file (T071). */
class HistoryCodecTest {
    private val word = Word("ſo", Box(1.0, 2.0, 3.0, 4.0))
    private val line = Line("l1", Box(10.0, 10.0, 100.0, 12.5), "l1", listOf(word, Word("uͤber")))
    private val bare = Line("l2", null, null, listOf(Word("Anmerkung")))
    private val odd =
        Line(
            "l3",
            null,
            null,
            listOf(Word("ꝛc.", extra = mapOf("conf" to JsonPrimitive(0.93)))),
            mapOf("tag" to JsonPrimitive("x")),
        )
    private val change = LineChange(3, "r1", 4, listOf(line, bare), listOf(odd))
    private val entries: List<HistoryEntry> =
        listOf(
            EditEntry(1, listOf(change)),
            EditEntry(2, listOf(change, change.copy(page = 9, region = "r2", index = 0, removed = emptyList()))),
            UndoEntry(3, 2, listOf(change.inverted())),
            RedoEntry(4, 2, listOf(change)),
            SavedEntry(4),
            EditEntry(5, emptyList()),
        )

    private fun text(entry: HistoryEntry) = CanonicalJson.write(HistoryCodec.encode(entry))

    @Test
    fun everyKindOfEntryComesBackAsItWas() {
        for (entry in entries) {
            assertEquals(entry, HistoryCodec.decode(HistoryCodec.encode(entry)), "$entry")
            // Through the text of the file, too.
            val parsed = CanonicalJson.parse(text(entry)) as JsonObject
            assertEquals(entry, HistoryCodec.decode(parsed), text(entry))
        }
    }

    @Test
    fun theTextOfAnEntryIsStable() {
        for (entry in entries) {
            val once = text(entry)
            val twice = text(HistoryCodec.decode(CanonicalJson.parse(once) as JsonObject))
            assertEquals(once, twice)
            assertTrue('\n' !in once)
        }
    }

    @Test
    fun theKeysAreAlphabeticalAndTheLinesAreWrittenAsInAPageFile() {
        val text = text(EditEntry(7, listOf(LineChange(3, "r1", 1, emptyList(), listOf(line)))))
        assertEquals(
            "{\"changes\": [{\"index\": 1, \"inserted\": [{\"box\": [10.0, 10.0, 100.0, 12.5], \"id\": \"l1\", " +
                "\"origin\": \"l1\", \"words\": [[\"ſo\", 1.0, 2.0, 3.0, 4.0], [\"uͤber\"]]}], \"page\": 3, " +
                "\"region\": \"r1\", \"removed\": [], \"type\": \"lines\"}], \"seq\": 7, \"type\": \"edit\"}",
            text,
        )
    }

    @Test
    fun theRegionFlagsAreWrittenOnlyWhenTheyAreFalse() {
        val plain = text(EditEntry(1, listOf(change)))
        assertTrue("regionBefore" !in plain && "regionAfter" !in plain)
        val made = LineChange(3, "r1", 0, emptyList(), listOf(bare), regionBefore = false)
        val entry = EditEntry(1, listOf(made))
        assertTrue("\"regionBefore\": false" in text(entry))
        assertEquals(entry, HistoryCodec.decode(HistoryCodec.encode(entry)))
        assertEquals(
            made.inverted(),
            (
                HistoryCodec.decode(
                    HistoryCodec.encode(EditEntry(1, listOf(made.inverted()))),
                ) as EditEntry
            ).changes.single(),
        )
    }

    @Test
    fun anEntryOfAnUnknownTypeIsKeptAsItIs() {
        val json = CanonicalJson.parse("{\"future\": [1, 2], \"seq\": 8, \"type\": \"status\"}") as JsonObject
        val entry = HistoryCodec.decode(json)
        assertEquals(UnknownEntry(json), entry)
        assertEquals("{\"future\": [1, 2], \"seq\": 8, \"type\": \"status\"}", text(entry))
    }

    @Test
    fun aChangeOfAnUnknownTypeIsKeptAndCannotBeInverted() {
        val change = CanonicalJson.parse("{\"page\": 2, \"to\": \"corrected\", \"type\": \"status\"}") as JsonObject
        val json =
            JsonObject(
                mapOf(
                    "type" to JsonPrimitive("edit"),
                    "seq" to JsonPrimitive(3),
                    "changes" to kotlinx.serialization.json.JsonArray(listOf(change)),
                ),
            )
        val entry = HistoryCodec.decode(json) as EditEntry
        assertEquals(UnknownChange(2, change), entry.changes.single())
        assertEquals(entry.changes.single(), entry.changes.single().inverted())
        assertEquals(json, HistoryCodec.encode(entry))
    }

    @Test
    fun aDamagedEntryIsAnErrorThatSaysWhatIsWrong() {
        fun fail(text: String) =
            assertFailsWith<FormatException> { HistoryCodec.decode(CanonicalJson.parse(text) as JsonObject) }.message!!
        assertTrue("seq" in fail("{\"changes\": [], \"type\": \"edit\"}"))
        assertTrue("step" in fail("{\"changes\": [], \"seq\": 1, \"type\": \"undo\"}"))
        assertTrue("changes" in fail("{\"seq\": 1, \"type\": \"edit\"}"))
        assertTrue("upTo" in fail("{\"type\": \"saved\"}"))
        assertTrue("type" in fail("{\"seq\": 1}"))

        fun withChange(change: String) = "{\"changes\": [$change], \"seq\": 1, \"type\": \"edit\"}"
        val noRegion = "{\"index\": 0, \"inserted\": [], \"page\": 1, \"removed\": [], \"type\": \"lines\"}"
        val badIndex =
            "{\"index\": -1, \"inserted\": [], \"page\": 1, \"region\": \"r1\", \"removed\": [], \"type\": \"lines\"}"
        assertTrue("region" in fail(withChange(noRegion)))
        assertTrue("index" in fail(withChange(badIndex)))
        assertTrue("History entry" in fail("{\"seq\": \"x\", \"type\": \"edit\", \"changes\": []}"))
    }
}
