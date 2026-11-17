package korrektor.search

import java.util.regex.Matcher

/**
 * One hit of a search (S10): in line [lineId] (the [lineNumber]th of the page in reading order, from 1) of [page], at
 * [start] until [end] of [lineText], the line's text exactly as it was when it was found. A hit lies within one line.
 */
data class Match(
    val page: Int,
    val lineId: String,
    val lineNumber: Int,
    val lineText: String,
    val start: Int,
    val end: Int,
) {
    init {
        require(start in 0 until end && end <= lineText.length) { "Not a place in the line: $start until $end" }
    }

    /** The text that was found. */
    val found: String get() = lineText.substring(start, end)

    /** What is in front of the hit, in the line. */
    val before: String get() = lineText.substring(0, start)

    /** What follows the hit, in the line. */
    val after: String get() = lineText.substring(end)
}

/**
 * The text that this hit turns into when it is replaced by [with]. A plain search replaces it by [with] as it is; a
 * regular expression ([literal] false) expands `$1`, `${name}` and `\$` as Kotlin's `Regex.replace` does. A failure
 * says why: the replacement refers to a group that is not there, or the line is not as the search saw it.
 */
fun Match.replacement(
    regex: Regex,
    with: String,
    literal: Boolean,
): Result<String> {
    if (literal) return Result.success(with)
    val matcher = regex.toPattern().matcher(lineText)
    return if (matcher.find(start) && matcher.start() == start && matcher.end() == end) {
        expand(matcher, with)
    } else {
        Result.failure(IllegalStateException("The line is not as it was when it was searched"))
    }
}

private fun Match.expand(
    matcher: Matcher,
    with: String,
): Result<String> =
    try {
        val out = StringBuffer()
        matcher.appendReplacement(out, with)
        // It has appended the text in front of the hit as well: what we want starts at the hit.
        Result.success(out.substring(start))
    } catch (e: IllegalArgumentException) {
        Result.failure(e)
    } catch (
        // The replacement names a group that the pattern does not have ("No group 3"): a message, not a crash.
        @Suppress("TooGenericExceptionCaught") e: IndexOutOfBoundsException,
    ) {
        Result.failure(e)
    }

/** A message for the user about a failed replacement. */
fun Throwable.replacementProblem(): String = "The replacement is not valid: ${message ?: this::class.simpleName}"
