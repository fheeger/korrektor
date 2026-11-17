package korrektor.search

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * What the user asks for (S10): the [text], searched literally unless [regex]. Case is ignored unless [matchCase]; with
 * [wholeWords] a hit must not touch a letter, a digit or an underscore on either side. Only the corrected text is
 * searched, exactly as stored: `s` does not find `ſ` (use `[sſ]` with [regex] for that).
 */
data class SearchQuery(
    val text: String,
    val matchCase: Boolean = false,
    val wholeWords: Boolean = false,
    val regex: Boolean = false,
)

/** What a [SearchQuery] turns into: something to search with, nothing to search for, or a reason it is not valid. */
sealed interface Compiled {
    /** The query as a [regex] and whether the replacement of its hits is [literal] (not a regex search). */
    class Ready(
        val regex: Regex,
        val literal: Boolean,
    ) : Compiled

    /** The text is empty: nothing is found, and that is not an error. */
    data object Empty : Compiled

    /** The regular expression is not valid; [message] says why. */
    class Invalid(
        val message: String,
    ) : Compiled
}

private const val WORD_CHAR = "[\\p{L}\\p{N}_]"

/** Makes the query ready for searching. */
fun SearchQuery.compile(): Compiled {
    if (text.isEmpty()) return Compiled.Empty
    val body =
        when {
            regex -> text
            matchCase -> Regex.escape(text)
            else -> ignoringCase(text)
        }
    val pattern = if (wholeWords) "(?<!$WORD_CHAR)(?:$body)(?!$WORD_CHAR)" else body
    // The flag folds ASCII letters only. Unicode folding would make `ſ` equal to `s`, so that a search for `ſt` found
    // every `st` too; variants of spelling are not equal here (that is D5). A regex that wants `ä` and `Ä` says so.
    val flags = if (regex && !matchCase) Pattern.CASE_INSENSITIVE else 0
    return try {
        Compiled.Ready(Pattern.compile(pattern, flags).toRegex(), literal = !regex)
    } catch (e: PatternSyntaxException) {
        Compiled.Invalid("Not a valid regular expression: ${e.description}")
    }
}

/**
 * [text] as a pattern that ignores case: each letter becomes a set of its cases, so `ä` finds `Ä`. A letter whose
 * other case is a different letter (`ſ` is the lower case of nothing, its upper case `S` has the lower case `s`) is
 * only itself.
 */
private fun ignoringCase(text: String): String =
    buildString {
        text.codePoints().forEach { cp ->
            val cases =
                listOf(cp, Character.toLowerCase(cp), Character.toUpperCase(cp), Character.toTitleCase(cp))
                    .filter { Character.toLowerCase(it) == Character.toLowerCase(cp) }
                    .distinct()
            if (cases.size == 1) {
                append(Pattern.quote(String(Character.toChars(cp))))
            } else {
                append('[')
                cases.forEach { appendCodePoint(it) } // letters, nothing in them is special
                append(']')
            }
        }
    }
