package korrektor.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import korrektor.pdf.PixelRegion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.roundToInt

/** A picture the source delivered: [image] shows [region] of the page at [scale]. */
private class Shown(
    val image: ImageBitmap,
    val scale: Double,
    val region: PixelRegion,
)

/**
 * Shows the page [state] says of the project's PDF: a toolbar with the navigation and the zoom, and below it the
 * page. The picture is always rendered by [source] for the visible part at the current scale, one image pixel per
 * screen pixel, so zooming in shows the scan's detail instead of a magnified low-resolution picture. While a new
 * picture is being made the previous one stays, scaled to the new scale, and is replaced as soon as the new one
 * arrives. The wheel scrolls, Ctrl+wheel zooms around the pointer. [state] holds the page, the zoom and the
 * position, so that the caller can keep them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PageViewer(
    source: PageSource,
    modifier: Modifier = Modifier,
    state: ViewerState = remember { ViewerState() },
    boxes: PageBoxSource = PageBoxSource { PageBoxes.NONE },
) {
    Column(modifier.fillMaxSize()) {
        // The two groups of controls wrap onto a second line when the panel is narrow (next to the text panel).
        FlowRow(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NavigationBar(state)
            ZoomToolbar(state)
        }
        PageArea(source, boxes, state, Modifier.weight(1f).fillMaxWidth())
    }
}

/** Previous and next page, "Page n of N", and a field to go to a page: Enter goes there, a wrong number is undone. */
@Composable
private fun NavigationBar(state: ViewerState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            { state.previous() },
            Modifier.semantics { contentDescription = "Previous page" },
            enabled = state.hasPrevious,
        ) { Text("‹") }
        OutlinedButton(
            { state.next() },
            Modifier.semantics { contentDescription = "Next page" },
            enabled = state.hasNext,
        ) { Text("›") }
        Text("Page ${state.page} of ${state.pageCount}")
        var typed by remember(state.page) { mutableStateOf(state.page.toString()) }
        // A plain field with a border: the Material text field is 56 dp high, too much for a toolbar.
        BasicTextField(
            value = typed,
            onValueChange = { typed = it },
            modifier =
                Modifier
                    .width(GO_TO_FIELD_WIDTH)
                    .height(GO_TO_FIELD_HEIGHT)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .semantics { contentDescription = "Go to page" }
                    .onKeyEvent { event ->
                        val enter = event.key == Key.Enter && event.type == KeyEventType.KeyDown
                        if (enter && !state.goToText(typed)) typed = state.page.toString()
                        enter
                    },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { field ->
                Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) { field() }
            },
        )
    }
}

private val GO_TO_FIELD_WIDTH = 72.dp
private val GO_TO_FIELD_HEIGHT = 36.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoomToolbar(state: ViewerState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(state.mode == ZoomMode.FIT_WIDTH, state::fitWidth, { Text("Fit width") })
        FilterChip(state.mode == ZoomMode.FIT_PAGE, state::fitPage, { Text("Fit page") })
        OutlinedButton(state::zoomOut, Modifier.semantics { contentDescription = "Zoom out" }) { Text("−") }
        OutlinedButton(state::zoomIn, Modifier.semantics { contentDescription = "Zoom in" }) { Text("+") }
        Text("${state.percent} %", Modifier.semantics { contentDescription = "Zoom" })
        FilterChip(state.showWordBoxes, { state.showWordBoxes = !state.showWordBoxes }, { Text("Word boxes") })
    }
}

/** What loading the current page has produced so far: the picture, or why there is none. */
private class PageLoad {
    var shown by mutableStateOf<Shown?>(null)
    var failure by mutableStateOf<String?>(null)
}

/**
 * Loads the page for the viewer, in three effects that restart when the page changes: its size (which tells the
 * state what to fit), the picture of what is visible (a new request cancels the old one), and, once there is a
 * picture and the source has a cache, the neighbours in the background. Navigating or zooming restarts the last one
 * and so cancels what was still running. One neighbour at a time, the next page first.
 */
@Composable
private fun rememberPageLoad(
    source: PageSource,
    page: Int,
    state: ViewerState,
): PageLoad {
    val load = remember(source, page) { PageLoad() }
    LaunchedEffect(source, page) {
        val size = tryOrFail({ load.failure = it }) { source.size(page) to source.nativeScale(page) }
        size?.let { (pageSize, native) -> state.setPage(pageSize, native) }
    }
    LaunchedEffect(source, page) {
        // `last` is the request being worked on: while it still covers what is visible, nothing new is asked.
        var last: PictureRequest? = null
        snapshotFlow { state.nextRequest(last) }
            .filterNotNull()
            .collectLatest { request ->
                last = request
                tryOrFail({ load.failure = it }) { source.render(page, request.scale, request.region) }?.let {
                    load.shown = Shown(it, request.scale, request.region)
                    load.failure = null
                }
            }
    }
    LaunchedEffect(source, page, load.shown, state.scale) {
        if (load.shown != null && source.caches) {
            val neighbours = listOf(page + 1, page - 1).filter { it in 1..state.pageCount }
            for (neighbour in neighbours) prefetch(source, state, neighbour)
        }
    }
    return load
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PageArea(
    source: PageSource,
    boxSource: PageBoxSource,
    state: ViewerState,
    modifier: Modifier = Modifier,
) {
    val page = state.page
    val load = rememberPageLoad(source, page, state)
    val boxes = rememberBoxes(boxSource, page, state)

    Box(
        modifier
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { state.setViewport(it.width, it.height) }
            .pointerHoverIcon(if (state.canPan) PointerIcon.Hand else PointerIcon.Default)
            .pointerInput(state) {
                detectDragGestures { change, drag ->
                    state.scrollBy(-drag.x.toDouble(), -drag.y.toDouble())
                    change.consume()
                }
            }.onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.first()
                val position = change.position
                state.wheel(
                    event.keyboardModifiers.isCtrlPressed,
                    position.x.toDouble(),
                    position.y.toDouble(),
                    deltaX = change.scrollDelta.x.toDouble(),
                    deltaY = change.scrollDelta.y.toDouble(),
                )
                change.consume()
            },
    ) {
        val picture = load.shown
        val reason = load.failure
        when {
            reason != null ->
                Text(
                    "The page cannot be shown: $reason",
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error,
                )
            picture == null -> Text("Loading…", Modifier.align(Alignment.Center))
            else -> PageCanvas(picture, state, page)
        }
        if (state.showWordBoxes && picture != null) BoxOverlay(boxes, state)
        Scrollbars(state)
    }
}

/** The scrollbars: one for each direction in which the page is larger than the viewport. */
@Composable
private fun BoxScope.Scrollbars(state: ViewerState) {
    if (state.contentHeight > state.viewportHeight) {
        VerticalScrollbar(
            ViewerScrollbarAdapter(state, vertical = true),
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().testTag("Vertical scrollbar"),
        )
    }
    if (state.contentWidth > state.viewportWidth) {
        HorizontalScrollbar(
            ViewerScrollbarAdapter(state, vertical = false),
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().testTag("Horizontal scrollbar"),
        )
    }
}

/** Draws the picture where its region of the page is at the current scale and position. */
@Composable
private fun PageCanvas(
    picture: Shown,
    state: ViewerState,
    page: Int,
) {
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Page $page" }) {
        // Exactly the right size when the picture was made for this scale and position; otherwise the old picture,
        // stretched and moved to where it belongs, until the new one is there.
        val ratio = state.scale / picture.scale
        drawImage(
            picture.image,
            dstOffset =
                IntOffset(
                    (state.originX + picture.region.x * ratio).roundToInt(),
                    (state.originY + picture.region.y * ratio).roundToInt(),
                ),
            dstSize =
                IntSize(
                    (picture.region.width * ratio).roundToInt(),
                    (picture.region.height * ratio).roundToInt(),
                ),
        )
    }
}

/** Runs [block]; a failure goes to [onFailure] (never a cancellation) and gives null. */
internal suspend fun <T> tryOrFail(
    onFailure: (String) -> Unit,
    block: suspend () -> T,
): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (
        // Whatever goes wrong with the PDF is shown in the viewer, not as a crash of the window.
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        onFailure(e.message ?: e::class.simpleName ?: "unknown error")
        null
    }

/** Renders what the viewer will ask for when [page] is shown, so it is in the cache by then. Errors are ignored. */
private suspend fun prefetch(
    source: PageSource,
    state: ViewerState,
    page: Int,
) {
    tryOrFail({ }) {
        val request = state.preview(source.size(page), source.nativeScale(page)).nextRequest(null)
        if (request != null) source.render(page, request.scale, request.region)
    }
}
