# T057 Keep track of which pages have unsaved changes

**Type:** task · **Status:** done · **Story:** S07 · **Task:** S07.1

## What needs doing
- **`BookEditor` knows the unsaved pages exactly.** Today `changedPages` is a set of page numbers that is never emptied. It becomes the set of pages whose corrected layer differs from what is on disk, and the editor gives the saver what it needs:
  - `unsaved(): Map<Int, PageLayer>`: a snapshot of the unsaved pages with their current layers (layers are immutable, so the snapshot cannot change under the saver).
  - `markSaved(page, layer)`: the page counts as saved **only if its current layer is the same object** as `layer`. If the user changed the page while it was being written, it stays unsaved. (Identity instead of revision counters: any edit makes a new layer.)
  - `onUnsaved`: a plain callback, called after every edit that changed a layer (not for `show`, not for a no-op edit). The saver (T058, T059) uses it to start its timer.
- The text "N pages changed, not saved yet" in the panel keeps working on `changedPages` until the indicator (T061) replaces it.
- A saved page stays in memory as before (going back to it costs no read); only the "unsaved" mark goes.
- New code in an extension file (`UnsavedPages.kt`) where possible, since `BookEditor` is at Detekt's limit of functions.

## Acceptance criteria
1. A new editor has no unsaved pages. An edit makes its page unsaved; an edit that changes nothing does not.
2. `unsaved()` returns the current layer of every unsaved page and nothing else.
3. `markSaved(page, layer)` with the layer from the snapshot clears the page; if the page was edited after the snapshot, it stays unsaved and the new layer is what `unsaved()` returns.
4. `markSaved` for a page that is not unsaved does nothing.
5. `onUnsaved` is called once per edit that changed a layer, on every kind of edit (text, split, join, add, delete), and not by `show` or `activate`.
6. Pages changed, saved, changed again are unsaved again.

## Approach
`BookEditor` (state and `edit`), `UnsavedPages.kt`. Unit tests in `UnsavedPagesTest` with a stub `readPage`.

## Dependencies
None.

## Decisions
- **`changedPages` keeps its name** and now means "pages with changes that are not on disk": it shrinks when the saver says a page is saved (`markSaved`). The panel text and the existing tests that use it are unchanged.
- **Identity, not counters:** `markSaved(page, layer)` clears a page only if its current layer is the same object. Layers are immutable and every edit makes a new one, so this is exact and needs no revision numbers.
- **Thread safety:** the saver runs on the IO threads while the user types on the UI thread. `changed` (the latest layer of every page changed in this session) is a `ConcurrentHashMap`, and the two places that change `changedPages` (an edit and `markSaved`) take one lock, so a page that was edited at the moment another was marked saved cannot drop out of the set (which would have been lost work).
- **`unsaved()` and `markSaved` are extensions** in `UnsavedPages.kt`: `BookEditor` is at the function limit of Detekt. They need `changed`, `changedLock` and the setter of `changedPages`, which are `internal`.
- **The callback is a plain property** (`onUnsaved`), not a Flow: the saver (T059) needs only "something changed", and a plain call is the same on any thread and in tests.
- A saved page stays in `changed` (and in memory), as before, so going back to a page costs no read.

## Progress
Done. `BookEditor` (`changed`, `changedLock`, `onUnsaved`, `changedPages`), `UnsavedPages.kt`. Tests: `UnsavedPagesTest` (10: new editor, no-op edit, current layers, markSaved, a page edited while written, markSaved of a page that is not unsaved, changed-saved-changed, a saved page stays in memory, the callback for every kind of edit, no callback for show/activate/moves). All other editor tests pass unchanged.
