# T064 Lock the project so that it cannot be opened twice

**Type:** task · **Status:** ready for development · **Story:** S07 · **Task:** S07.7

## What needs doing
- **`ProjectLock`** (format package): `acquire(folder)` takes the lock of a project, `close()` gives it back.
  - The lock is an **operating system file lock** on `<project>/.lock` (`FileChannel.tryLock`), held for as long as the project is open. The file itself is only the place for the lock: a crash releases the lock with the process, and the file left behind does not block anything (it is simply locked again). That is why there is no "is this lock stale?" check to get wrong.
  - Two windows of one app instance, and two instances (processes): the second `acquire` fails. In one JVM `tryLock` throws `OverlappingFileLockException`, in two processes it returns null; both become `ProjectLockedException` ("The project <title> is already open in another window or instance").
  - The file holds a line with the process id and the time, for a human who looks; nothing reads it.
  - `close()` releases the lock and deletes `.lock` (best effort).
- **`AppModel` takes the lock** when it opens or creates a project (`openProject`, `openCreated`): first the lock, then `Project.open`; if opening fails the lock is released. The previous project's lock is released when another project is opened. A locked project: `AppModel.error` shows the message, the current project stays open.
- The format layer (`Project.open`, tests that open a folder twice) is not locked: only the app is.
- `docs/project-format.md` describes the `.lock` file: what it is, that it is never content, that an old one is harmless.
- `Project.copyTo` (T062) and `AtomicFile.cleanupTemp` ignore it.

## Acceptance criteria
1. A first `acquire` works; a second one for the same folder in the same JVM fails with `ProjectLockedException` until the first is closed; then it works.
2. A second **process** cannot acquire a lock held by the first (test starts a child JVM that holds the lock, signals it holds it, and the test fails to acquire; after the child is destroyed, acquiring works).
3. A `.lock` file left by a crash (child killed with `destroyForcibly`) does not block `acquire`.
4. A file `.lock` with unrelated content, or an empty one, does not block.
5. `AppModel.openProject` of a locked project sets a clear error, keeps the current project, and does not touch the files; opening a project the user has open already (the same folder again) is the same message.
6. After the app model opens another project, the first project can be opened again elsewhere.
7. A project folder that is read-only (no `.lock` can be created) gives a clear error and is not opened? Decide while building: opening a read-only project cannot save, so it is refused with "no write permission" and recorded.

## Approach
`ProjectLock.kt`, `AppModel`, `docs/project-format.md`. Tests: `ProjectLockTest` (same JVM), `ProjectLockProcessTest` (child JVM from the test classpath), `AppModelTest` additions.

## Dependencies
None.

## Decisions
(filled in while building)

## Progress
Not started.
