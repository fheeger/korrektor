package korrektor.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TypingGroupsTest {
    private fun edit(
        old: String,
        new: String,
    ) = TypingGroups.textEdit(old, new)

    /**
     * Types the texts one after the other on page 1, line l1, [gap] ms apart. The first edit always starts a step; the
     * answer is, for each edit after it, whether it went on with the step before.
     */
    private fun run(
        vararg texts: String,
        gap: Long = 100,
    ): List<Boolean> {
        var run: TypingRun? = null
        var time = 0L
        var previous = texts.first()
        val answers = mutableListOf<Boolean>()
        for ((i, text) in texts.drop(1).withIndex()) {
            time += gap
            val e = edit(previous, text)
            val goes = TypingGroups.continues(run, 1, "l1", e, time)
            run =
                if (goes) TypingGroups.extended(checkNotNull(run), e, time) else TypingGroups.started(1, "l1", e, time)
            if (i > 0) answers += goes
            previous = text
        }
        return answers
    }

    @Test
    fun theEditIsFoundByCuttingOffTheCommonStartAndEnd() {
        assertEquals(TextEdit(3, 0, 1, false), edit("abc", "abcd"))
        assertEquals(TextEdit(1, 1, 0, false), edit("abc", "ac"))
        assertEquals(TextEdit(1, 1, 1, false), edit("abc", "axc"))
        assertEquals(TextEdit(0, 0, 3, false), edit("", "abc"))
        assertEquals(TextEdit(3, 0, 0, false), edit("abc", "abc"))
        // Repeated characters: the same text however it is cut.
        assertEquals(TextEdit(2, 0, 1, false), edit("aa", "aaa"))
    }

    @Test
    fun aSpaceAfterAWordStartsANewWord() {
        assertTrue(edit("Die", "Die ").startsWord)
        assertFalse(edit("Die ", "Die  ").startsWord, "a second space is no new word")
        assertFalse(edit("", " ").startsWord, "a space at the start of the line is no word end")
        assertFalse(edit("Die", "Dien").startsWord)
    }

    @Test
    fun theCharactersOfAWordAreOneStep() {
        assertEquals(listOf(true, true, true), run("", "D", "Di", "Die", "Dien"))
    }

    @Test
    fun aSpaceStartsAStepAndTheNextWordGoesOnInIt() {
        // "Die" | " Welt" | " i…": the first two edits make the first step, so the answers start with the third.
        val goes =
            run("", "D", "Di", "Die", "Die ", "Die W", "Die We", "Die Wel", "Die Welt", "Die Welt ", "Die Welt i")
        assertEquals(listOf(true, true, false, true, true, true, true, false, true), goes)
    }

    @Test
    fun aPauseOfASecondEndsTheStep() {
        assertEquals(listOf(true), run("a", "ab", "abc", gap = 999))
        assertEquals(listOf(false), run("a", "ab", "abc", gap = 1000))
        assertEquals(listOf(false), run("a", "ab", "abc", gap = 5000))
    }

    @Test
    fun anotherPlaceStartsAStep() {
        // typing at the end, then in the middle of the text
        assertEquals(listOf(false), run("abcdef", "abcdefg", "abcxdefg"))
    }

    @Test
    fun backspaceInThePlaceGoesOn() {
        assertEquals(listOf(true, true), run("abc", "abcd", "abc", "ab"))
        // deleting forwards from a place
        assertEquals(listOf(true), run("abcdef", "abcdf", "abcf"))
    }

    @Test
    fun aPasteAndAReplacedSelectionAreStepsOfTheirOwnAndTheNextEditStartsAnother() {
        assertEquals(listOf(false, false), run("a", "ab", "abPASTED", "abPASTEDc"))
        assertEquals(listOf(false), run("abc", "xc", "xcd"))
    }

    @Test
    fun anotherLineOrPageStartsAStep() {
        val e = edit("a", "ab")
        val run = TypingRun(1, "l1", 0, 0, 1)
        assertTrue(TypingGroups.continues(run, 1, "l1", e, 10))
        assertFalse(TypingGroups.continues(run, 1, "l2", e, 10))
        assertFalse(TypingGroups.continues(run, 2, "l1", e, 10))
        assertFalse(TypingGroups.continues(null, 1, "l1", e, 10))
    }

    @Test
    fun aLetterInsertedWithItsMarkIsOneKey() {
        // "u" with a small e above, from the Insert menu, in the middle of a word: one step with the typing around it.
        assertTrue(edit("b", "buͤ").isOneKey)
        assertEquals(listOf(true, true), run("", "b", "buͤ", "buͤc"))
        assertTrue(edit("u", "uͤ").isOneKey, "a mark on its own")
        assertFalse(edit("b", "buv").isOneKey)
        assertFalse(edit("b", "buͤv").isOneKey)
    }

    @Test
    fun aRunStartsOnlyWithAKey() {
        assertNotNull(TypingGroups.started(1, "l1", edit("a", "ab"), 5))
        assertNull(TypingGroups.started(1, "l1", edit("a", "aPASTE"), 5))
    }
}
