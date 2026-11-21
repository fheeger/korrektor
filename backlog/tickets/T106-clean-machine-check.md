# T106 Check the zip in a clean Windows environment

**Type:** task · **Status:** ready for development · **Story:** S12 · **Task:** S12.7

## What needs doing
- Unzip the release zip in Windows Sandbox (or a machine without Java and without the project), start the app and work through: new project from the sample, edit a line, save, close and reopen, undo after reopening, mark a page, find and replace, export, the About dialog.
- Record the result and what could not be checked in the story report.

## Acceptance criteria
1. Each step works, or the problem becomes a ticket.
2. The report says where it ran.

## Approach
By hand. Windows Sandbox needs an optional Windows feature; if it is not available the check is done on this machine with `PATH` and `JAVA_HOME` cleared, and the report says so.

## Dependencies
T102, T105

## Decisions
(none yet)

## Progress
Not started.
