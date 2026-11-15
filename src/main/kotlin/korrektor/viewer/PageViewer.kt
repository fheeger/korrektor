package korrektor.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
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

/** What the viewer asks the source for: the visible [region] at [scale]. A new request cancels the old one. */
private data class Request(
    val scale: Double,
    val region: PixelRegion,
)

/**
 * Shows [page] of the project's PDF: a toolbar with the zoom, and below it the page. The picture is always rendered
 * by [source] for the visible part at the current scale, one image pixel per screen pixel, so zooming in shows the
 * scan's detail instead of a magnified low-resolution picture. While a new picture is being made the previous one
 * stays, scaled to the new scale, and is replaced as soon as the new one arrives. The wheel scrolls, Ctrl+wheel
 * zooms around the pointer. [state] holds the zoom and the position, so that the caller can keep it.
 */
@Composable
fun PageViewer(
    source: PageSource,
    page: Int,
    modifier: Modifier = Modifier,
    state: ViewerState = remember { ViewerState() },
) {
    Column(modifier.fillMaxSize()) {
        ZoomToolbar(state)
        PageArea(source, page, state, Modifier.weight(1f).fillMaxWidth())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoomToolbar(state: ViewerState) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(state.mode == ZoomMode.FIT_WIDTH, state::fitWidth, { Text("Fit width") })
        FilterChip(state.mode == ZoomMode.FIT_PAGE, state::fitPage, { Text("Fit page") })
        OutlinedButton(state::zoomOut, Modifier.semantics { contentDescription = "Zoom out" }) { Text("−") }
        OutlinedButton(state::zoomIn, Modifier.semantics { contentDescription = "Zoom in" }) { Text("+") }
        Text("${state.percent} %", Modifier.semantics { contentDescription = "Zoom" })
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PageArea(
    source: PageSource,
    page: Int,
    state: ViewerState,
    modifier: Modifier = Modifier,
) {
    var shown by remember(source, page) { mutableStateOf<Shown?>(null) }
    var failure by remember(source, page) { mutableStateOf<String?>(null) }

    LaunchedEffect(source, page) {
        val size = tryOrFail({ failure = it }) { source.size(page) to source.nativeScale(page) }
        size?.let { (pageSize, native) -> state.setPage(pageSize, native) }
    }
    LaunchedEffect(source, page) {
        snapshotFlow { state.visibleRegion?.let { Request(state.scale, it) } }
            .filterNotNull()
            .collectLatest { request ->
                tryOrFail({ failure = it }) { source.render(page, request.scale, request.region) }?.let {
                    shown = Shown(it, request.scale, request.region)
                    failure = null
                }
            }
    }

    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { state.setViewport(it.width, it.height) }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.first()
                val position = change.position
                state.wheel(
                    change.scrollDelta.y.toDouble(),
                    event.keyboardModifiers.isCtrlPressed,
                    position.x.toDouble(),
                    position.y.toDouble(),
                )
                change.consume()
            },
    ) {
        val picture = shown
        val reason = failure
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
private suspend fun <T> tryOrFail(
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
