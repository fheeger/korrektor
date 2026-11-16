# T061 The save status indicator

**Type:** task · **Status:** ready for development · **Story:** S07 · **Task:** S07.4

## What needs doing
- **A small indicator** in the text panel's header row (where "N pages changed, not saved yet" is now, which it replaces) with three states:
  - **Saved**: nothing unsaved, nothing being written;
  - **Saving…**: a write is running;
  - **Not saved**: pages are unsaved and no write is running (waiting for the timer, or the last save failed; red when it failed).
- `AutoSaver.status` (an enum, derived from `editor.changedPages`, `saving` and `error`) is the one place that decides; the UI only shows it. Test tag `save status`.
- A tooltip or the text itself says how many pages are unsaved when there are any? No: the three words are enough; keep it small. The page count goes away with the old text.
- No flicker: "Saving…" for a write that takes a few milliseconds should not flash; show "Saving…" only for writes that run longer than 300 ms? Decide while building and record it (the story asks for the three states, not for timing).

## Acceptance criteria
1. A freshly opened project shows "Saved".
2. After an edit it shows "Not saved"; while the (slow, fake) writer runs "Saving…"; after the write "Saved" (UI test with a blocking writer).
3. A failing writer shows "Not saved" in the error colour, with the warning of T060.
4. An edit during "Saving…" ends in "Not saved" and then "Saved" after the next save.
5. The old "N pages changed, not saved yet" text is gone, and nothing else (README) refers to it.

## Approach
`AutoSaver.status`, the panel header in `TextPanel.kt`. Tests: `SaveStatusTest` (status logic), UI test in `SaveStatusUiTest`.

## Dependencies
T058, T060.

## Decisions
(filled in while building)

## Progress
Not started.
