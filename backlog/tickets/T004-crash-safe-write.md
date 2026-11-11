# T004 Crash-safe file writing

**Type:** task · **Status:** ready for development · **Story:** S01 · **Task:** S01.6

One function that replaces a file so that an interruption never leaves a broken file.

## What needs doing
`AtomicFile.write(path: Path, bytes: ByteArray)` in `AtomicFile.kt`:
1. Write the bytes to a temporary file in the same folder (`<name>.tmp`).
2. Flush it to disk (`FileChannel.force`).
3. Rename it over the target with `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`.
4. If anything fails, delete the temporary file and rethrow; the target is untouched.

Also `AtomicFile.cleanupTemp(dir)`: deletes leftover `*.tmp` files from an earlier crash. It runs when a project is opened (T005).

## Acceptance criteria
1. After a successful write the target has the new content and no `.tmp` file remains.
2. If writing fails halfway (simulated), the old content of the target is unchanged and no `.tmp` file remains.
3. If the target doesn't exist, it is created; if it does, it is replaced.
4. Leftover `.tmp` files are removed by `cleanupTemp`, and nothing else is.

## Approach
- Unit tests in `AtomicFileTest` on a temporary directory. Failure is simulated by an injectable write step (an internal parameter that throws after half the bytes).
- A real crash (killing the process during a write) is tested with S07's crash test.

## Dependencies
None.

## Decisions
- The temp file is in the same folder, because an atomic rename only works within one file system.
- The folder itself is not fsynced: Java has no portable way to do it. If a power cut loses the rename, the old, intact file remains. Autosave (S07) keeps that loss small.

## Progress
Not started.
