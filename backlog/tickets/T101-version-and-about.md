# T101 App version in one place, and the About dialog

**Type:** task · **Status:** ready for development · **Story:** S12 · **Task:** S12.2

## What needs doing
- `build.gradle.kts` holds the version `0.1.0-beta.1` (`version = ...`). The app reads it at run time from a generated resource file, so the running app and the build cannot disagree.
- Help > About korrektor (no shortcut, works without a project) opens a small dialog: name, version, license (GPL-3.0), a link to the repository and a close button.

## Acceptance criteria
1. The version comes from `build.gradle.kts` only; a test checks that `AppInfo.version` equals the Gradle version.
2. The Help menu has "About korrektor", also with no project open; it opens and closes the dialog.
3. The dialog shows the version, the license and the repository address.
4. The menu test (every shortcut action is in the menus) still holds; the new entry needs no shortcut.

## Approach
A Gradle task generates `app.properties` into the resources. `AppInfo`, `AboutDialog`, an entry in the Help menu in `Main.kt` and a flag `aboutOpen` in `AppModel`. Unit test for the version, a Compose UI test for the dialog and the menu entry.

## Dependencies
none

## Decisions
(none yet)

## Progress
Not started.
