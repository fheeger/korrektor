# T058 The saver: write the unsaved pages in the background

**Type:** task · **Status:** done · **Story:** S07 · **Task:** S07.3

## What needs doing
- **`AutoSaver`** (package `korrektor.editor`; plain Kotlin, no UI) for one open project. Its core is `suspend fun saveNow(): SaveOutcome`:
  1. take the snapshot `editor.unsaved()`;
  2. write each page with `writePage(page, layer)` on the IO dispatcher, in page order, through `Project.writePage` (so with the crash-safe writing of S01.6: temp file, flush, atomic rename);
  3. after each page `editor.markSaved(page, layer)`;
  4. on the first failure stop, keep the rest unsaved and report the failure (T060 adds warning and retry).
- Saves never overlap (one `Mutex`): a second `saveNow` waits for the first, then writes whatever is unsaved by then.
- **Only changed pages are written.** The original OCR layer, `source.pdf`, `project.json` and the other pages are not touched (N4). Pages that were saved and not changed since are not written again.
- **Typing is never blocked:** the snapshot is immutable, the writing is on the IO dispatcher, and the editor is not locked while it runs. An edit during a save makes a new layer, so the page stays unsaved and is written by the next save.
- **State for the indicator and the warning:** `saving: Boolean` and `error: String?` as Compose state (read by the UI, changed by the saver on whichever thread; use `mutableStateOf`).
- The writer is a constructor parameter (`(Int, PageLayer) -> Unit`, default `project::writePage`) so that tests can make writing slow or failing.

## Acceptance criteria
1. After `saveNow` the page files of the changed pages contain the corrected layers (read back with `Project.readPage`) and `editor.changedPages` is empty.
2. Pages without changes, the original layers (`*.ocr.json`), `project.json` and `source.pdf` are byte for byte the same as before (compare the folder before and after).
3. A page edited during a save is written again by the next save and not lost; the earlier layer never overwrites the later one.
4. Two `saveNow` calls at once write in turn, not together (a slow fake writer records no overlap).
5. A failing write leaves that page and all after it unsaved, pages before it saved, `error` set, and the exception does not escape `saveNow`.
6. `saving` is true while the writer runs and false before and after.
7. Typing during a save is not delayed: the editor's `setLineText` does not wait for the writer (test with a writer that blocks until the test releases it).

## Approach
`AutoSaver.kt`; `UnsavedPages` from T057. Tests: `AutoSaverTest` with a real project (generated PDF) and fake writers; byte comparison like `EditorFlowTest`.

## Dependencies
T057.

## Decisions
- **A failure is a result, not an exception:** `saveNow()` returns `SaveOutcome.Done` or `SaveOutcome.Failed(page, reason)` and also sets `error`. Any `Exception` of a write counts (disk full, access denied, a vanished folder, a `FormatException`); only cancellation passes through. The saver stops at the first failure: later pages would most likely fail for the same reason, and the order of the pages is kept simple.
- **A readable reason:** a `FileSystemException` becomes "<file name>: <reason>" and other `IOException`s use the same `reason()` helper as the format package.
- **No overlap, no lock on the editor:** one `Mutex` for the saves; the editor is only asked for a snapshot (`unsaved()`) and told afterwards (`markSaved`), both short and thread-safe (T057). The writing itself runs on the IO dispatcher with nothing held. A test blocks the writer and shows that a hundred edits go through meanwhile.
- **Cancelling:** the caller's cancellation stops the loop before the next page (`ensureActive`); a page that is being written is finished, because a file write cannot be interrupted and a half-written page does not exist anyway (temp file and rename, S01.6).
- **An empty snapshot clears `error`:** with nothing to save there is nothing wrong any more (for example the pages were saved elsewhere, T062).
- **The writer is a constructor parameter** (`project::writePage` in the app, wired in T059), the dispatcher too.
- **Not in this ticket:** when to save (T059), retrying and the warning (T060), the indicator (T061).

## Progress
Done. `AutoSaver` (`saveNow`, `saving`, `error`, `SaveOutcome`). Tests: `AutoSaverTest` (11, with a real generated project and fake writers: changed pages written and cleared; only the changed file differs, byte for byte; a saved page is not written again; a page edited while written is not lost and the old layer never overwrites the new one; no two writes at once; a failing write keeps that page and the ones after it; the next save works and clears the error; a real file-system failure gives a reason; `saving` is true while the writer runs; typing is not held up; nothing unsaved writes nothing).
