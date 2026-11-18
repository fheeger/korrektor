package korrektor.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import korrektor.editor.BookEditor
import korrektor.format.PageLayer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Where to search: the page that is shown, or the whole book. */
enum class SearchScope { PAGE, BOOK }

/** How long the search waits after the last change of the query, so that typing does not start a search per letter. */
const val SEARCH_PAUSE_MS = 150L

/**
 * Everything the search and replace panel shows and does (S10), as Compose state, so that it can be tested without a
 * window: the query, the replacement, the scope, whether the panel and its replace field are shown, and the results,
 * which fill up while a search runs in the background and can be cancelled.
 *
 * The pages are read and searched on [io], and the results are handed over by coroutines on [ui] (a default
 * dispatcher in the app). Compose state may be changed from any thread, but a new search and a batch of the old
 * one must not interleave: both change the results under [lock]. The search finds what the user sees: the
 * corrections of this session count, saved or not (`BookEditor.currentLayer`).
 */
class SearchModel(
    internal val editor: BookEditor,
    private val pageCount: Int,
    private val ui: CoroutineDispatcher = Dispatchers.Default,
    internal val io: CoroutineDispatcher = Dispatchers.IO,
    internal val pause: Long = SEARCH_PAUSE_MS,
    internal val chunk: Int = DEFAULT_CHUNK,
) {
    private val scope = CoroutineScope(SupervisorJob() + ui)
    private var job: Job? = null
    internal val lock = Any()
    internal var generation = 0
    internal var lastHitPage = 0
    internal var layerAfterReplace: PageLayer? = null

    /** Whether the panel is shown. */
    var open: Boolean by mutableStateOf(false)

    /** Whether the replace field is shown (S10: one panel for finding and replacing). */
    var showReplace: Boolean by mutableStateOf(false)

    /** Counts the requests to put the cursor in the search field and select its text (Ctrl+F, Ctrl+H). */
    var fieldRequests: Int by mutableIntStateOf(0)
        private set

    /** What is searched for and how. Change it with [change]. */
    var query: SearchQuery by mutableStateOf(SearchQuery(""))
        private set

    /** The text that replaces a hit (S10, replace mode). */
    var replacement: String by mutableStateOf("")

    /** Where is searched. Change it with [changeScope]. */
    var scopeOf: SearchScope by mutableStateOf(SearchScope.BOOK)
        private set

    /** The hits found so far, in page order. They are added while the search runs. */
    val results = mutableStateListOf<Match>()

    /** Whether a search is running (or waiting for the pause to end). */
    var searching: Boolean by mutableStateOf(false)
        internal set

    /** How many pages the running or last search has done, and how many it will do. */
    var pagesDone: Int by mutableIntStateOf(0)
        internal set
    var pagesTotal: Int by mutableIntStateOf(0)
        private set

    /** How many different pages have hits. */
    var pagesWithHits: Int by mutableIntStateOf(0)
        internal set

    /** Whether a replacement is being made (pages are read in the background): no second one starts meanwhile. */
    var replacing: Boolean by mutableStateOf(false)
        internal set

    /** What the last replacement did, for the user: "Replaced 40 matches on 12 pages"; null after a new search. */
    var outcome: String? by mutableStateOf(null)
        internal set

    /** Why there are no results: the query is not valid, or pages could not be read. Null if all is well. */
    var message: String? by mutableStateOf(null)
        internal set

    /** The index in [results] of the hit that is shown, or -1 (see `SearchNavigation.kt`). */
    var current: Int by mutableIntStateOf(-1)
        internal set

    /** The indices in [results] of the hits whose box is checked (S10, replace mode); none is checked at first. */
    var marked: Set<Int> by mutableStateOf(emptySet())
        internal set

    /**
     * Counts the requests by key to replace the hit that is shown and go on, and to replace the checked hits (T091).
     * The panel carries them out on the UI thread, where replacing is done; the shortcut only asks.
     */
    var replaceOneRequests: Int by mutableIntStateOf(0)
        private set
    var replaceMarkedRequests: Int by mutableIntStateOf(0)
        private set

    /** The compiled form of the query of the last search; what replaces needs it for `$1`. */
    internal var compiled: Compiled.Ready? = null
        private set

    /**
     * Opens the panel and puts the cursor in its field, with the replace field shown (Ctrl+H) or hidden (Ctrl+F: in
     * replace mode it goes back to searching). Query, options, scope and results are kept (T091).
     */
    fun show(replace: Boolean) {
        showReplace = replace
        open = true
        fieldRequests++
    }

    /** Asks the panel to replace the hit that is shown and go on; only in replace mode. */
    fun requestReplaceOne() {
        if (open && showReplace) replaceOneRequests++
    }

    /** Asks the panel to replace the checked hits; only in replace mode. */
    fun requestReplaceMarked() {
        if (open && showReplace) replaceMarkedRequests++
    }

    /** Closes the panel; the query and the results are kept for the next time. */
    fun hide() {
        open = false
    }

    /** Sets a new query and searches. */
    fun change(new: SearchQuery) {
        if (new == query) return
        query = new
        search()
    }

    /** Searches the page shown or the whole book from now on, and searches. */
    fun changeScope(new: SearchScope) {
        if (new == scopeOf) return
        scopeOf = new
        search()
    }

    /** The page shown has changed or its text was edited: a search of that page is done again. */
    fun pageChanged() {
        // The page that a replacement has just changed is not searched again: the results are already right.
        if (open && scopeOf == SearchScope.PAGE && editor.layer !== layerAfterReplace) search()
    }

    /** Stops a running search. What it has found stays. */
    fun cancel() {
        synchronized(lock) {
            generation++
            job?.cancel()
            searching = false
        }
    }

    /** Stops everything for good (the project is closed). */
    fun close() {
        cancel()
        scope.cancel()
    }

    /**
     * Starts a new search after the pause, and drops the results of the old one: a search that is still running is
     * cancelled first, so none of its hits arrive after this call.
     */
    fun search() {
        synchronized(lock) {
            cancel()
            results.clear()
            current = -1
            marked = emptySet()
            outcome = null
            pagesDone = 0
            pagesWithHits = 0
            lastHitPage = 0
            message = null
            compiled = null
            val ready = readyQuery() ?: return
            compiled = ready
            val mine = generation
            val pages = if (scopeOf == SearchScope.PAGE) editor.page..editor.page else 1..pageCount
            pagesTotal = pages.last - pages.first + 1
            searching = true
            job = scope.launch { run(mine, pages, ready) }
        }
    }
}
