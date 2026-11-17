package korrektor.editor

import korrektor.format.PageLayer

/**
 * The corrected layer of page [number] as it is now: what was corrected in this session (not saved yet, maybe), else
 * what the project has. It reads the page file on the calling thread if it must, so not on the UI thread. For the
 * search (S10), which must find what the user sees and not what the last save wrote.
 */
internal fun BookEditor.currentLayer(number: Int): PageLayer = changed[number] ?: readPage(number)
