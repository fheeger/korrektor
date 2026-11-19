# T094 Markdown export with escaping

**Type:** task · **Status:** ready for development · **Story:** S11 · **Task:** S11.3

## What needs doing
- The Markdown format of the renderer of T093: hard break `\` for kept line breaks, the title as `# heading` in the header, the page marker as its own paragraph.
- Escaping: characters that Markdown reads as markup are escaped in the text of the lines and in the metadata: `\`, `` ` ``, `*`, `_`, `[`, `]`, `<`, `>`, `~`, and `&` when it begins an entity. At the start of a paragraph or of a kept line also `#`, `-`, `+`, `=`, a number followed by `.` or `)`, and a leading space or tab.

## Acceptance criteria
1. `1. Erstens`, `- x`, `+ x`, `# x`, `> x`, `===` and `---` do not become a list, heading, quote, setext heading or rule.
2. `*a*`, `_a_`, `**a**`, `` `a` ``, `~~a~~`, `[a](b)`, `<b>a</b>` and `&amp;` show as the typed text.
3. A backslash in the text stays one backslash.
4. Kept line breaks use `\` at the end of every line of a paragraph but the last; joined text is one paragraph per page.
5. Rendered by a CommonMark renderer, the text of every line is the stored text (tested with a renderer in the tests).
6. The title is `# Title` with escaped characters; the other metadata are lines of one paragraph.
7. Text without special characters is not changed (no needless backslashes).

## Approach
`MarkdownEscape.kt` (pure functions), the Markdown style in the renderer. A CommonMark library for the tests only (`org.commonmark:commonmark`, small, test scope) renders the output, to check that the stored text comes out. Unit tests with the edge cases.

## Dependencies
T093.

## Decisions
(none yet)

## Progress
Not started.
