# T064 Lock the project so that it cannot be opened twice

**Type:** task · **Status:** done · **Story:** S07 · **Task:** S07.7

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
- **An operating system file lock, not a "lock file with a pid":** the lock is `FileChannel.tryLock` on `.lock`, held while the project is open. When the process ends, however it ends, the system releases it, so a crash can never leave a lock that blocks. A scheme with a pid in the file needs a check "is that process alive?", which can be wrong (pid reuse, another user, another machine). The line in the file (process and time) is only for a human.
- **The file is never deleted.** Deleting it on close could remove the file under a process that just took the lock on it, and a third process would then lock a new file: two holders. A file that stays is harmless (documented in `docs/project-format.md`). The lock is held on the file's content region by the channel, not by the file's existence.
- **Same JVM and other processes both covered:** `tryLock` returns null for another process and throws `OverlappingFileLockException` for another channel of the same JVM (two windows, or the same project twice); both are "already open". Tested with real second processes (a child JVM from the test classpath, also killed with `destroyForcibly`, as a crash).
- **The app locks, the format layer does not:** `Project.open` stays free of locks (many tests and tools read projects), `AppModel` takes the lock when it opens or creates a project, and gives it back when another is opened (after the new one is open, so that a failed open changes nothing). A new `AppModel.closeProject()` (extension in `ProjectClosing.kt`) closes the project and gives the lock back; T062 uses it for closing with a flush first.
- **Lock only a project folder:** the lock is taken only if the folder has a `project.json`, so choosing any other folder does not leave a `.lock` file in it (tested); the usual error comes from `Project.open`.
- **A folder that cannot be written to is refused,** with the reason ("cannot be opened for writing: access denied"), because it could not be saved to either. Tested with a read-only `.lock` file (skipped where the file system does not support read-only files).
- **Tests that opened one project in several `AppModel`s in turn** (to simulate sessions) now close the earlier model first (`closeProject`), or leave out `.lock` when they compare the folder byte for byte. The lock is the point of the story.
- **After a kill the system may need a moment** to give the handles back: the test of the killed process tries for up to ten seconds. A real restart after a crash is never that fast.

## Progress
Done. `ProjectLock` (`acquire`, `close`, `ProjectLockedException`), `AppModel` (lock on open and create, released on switching), `closeProject`, docs (`project-format.md`, `decisions.md`, `architecture.md`, README). Tests: `ProjectLockTest` (10: same JVM, closing twice, different folders, a file left behind, other content, the line in the file, a read-only lock file, another process holds the lock, a killed process leaves a file that does not block), `LockHolder` (the child process), `AppModelLockTest` (7: second window gets "already open" with the folder name, same window twice, the open project stays, switching gives the first back, a new project is locked, no lock file in a non-project folder, a failed open releases the lock).
