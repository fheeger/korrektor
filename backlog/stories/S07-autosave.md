# S07 Autosave

**Features:** N4 · **Status:** ready for development

As a reader of historical sources, I want my corrections to be saved automatically
so that I never lose work, even if the app crashes.

S07 decides when and how changes from S05, S06 and later S09 (page status) are saved, and what happens when saving fails. It builds on the crash-safe writing from S01 (S01.6). The undo history belongs to S08.

## Acceptance criteria
- There is no save step you have to remember, and you're never asked to save when closing.
- Changes are saved after 1 s without typing. During continuous typing they're saved at least every 10 s. They're also saved when you switch pages, close the project or close the app.
- If the app or computer crashes, you lose at most the last 10 s of typing. The project always opens afterwards, in its last saved state.
- Only pages that have changed are saved. The original PDF and the original OCR layer are never written to (N4).
- Saving runs in the background and never interrupts typing, even in a 1000-page book (N1).
- A small indicator shows "Saved", "Saving…" or "Not saved".
- When saving fails (disk full, folder gone, no write permission):
  - You see a clear warning.
  - Your changes stay in memory and saving is retried.
  - If you close the app while saving keeps failing, you're warned first and offered the chance to save the project to a different location.
- Ctrl+S does nothing and shows no message, so pressing it out of habit doesn't disrupt your work.
- The same project can't be open twice at the same time, whether in two app windows or two instances. A second attempt gets a clear message. A lock left behind by a crash doesn't block opening the project.

## Tasks
- [ ] S07.1 Keep track of which pages have unsaved changes
- [ ] S07.2 Save triggers: 1 s pause in typing, at least every 10 s, page switch, closing
- [ ] S07.3 Save in the background, using the crash-safe writing from S01.6
- [ ] S07.4 Save status indicator
- [ ] S07.5 Handle failed saves: warning, retry, warning when closing, saving to another location
- [ ] S07.6 Catch Ctrl+S and ignore it silently
- [ ] S07.7 Lock the project so it can't be opened twice, and clean up a lock left behind by a crash
- [ ] S07.8 Tests:
  - Kill the app while typing and while saving, then check that the project opens and at most the last 10 s are lost.
  - Simulate failed saves.
  - Measure saving speed in a 1000-page book.
