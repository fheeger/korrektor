# T057 Keep track of which pages have unsaved changes

**Type:** task · **Status:** ready for development · **Story:** S07 · **Task:** S07.1

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
(filled in while building)

## Progress
Not started.
