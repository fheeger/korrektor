package korrektor.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import korrektor.pdf.PixelRegion
import kotlinx.coroutines.CancellationException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** What the viewer shows for the page: not there yet, the picture, or why there is none. */
private sealed interface Picture {
    data object Loading : Picture

    class Ready(
        val image: ImageBitmap,
    ) : Picture

    class Failed(
        val reason: String,
    ) : Picture
}

/** The tallest picture drawn at once, in pixels. T027/T028 replace the whole-page picture by the visible part. */
private const val MAX_PICTURE_HEIGHT = 8_000

/**
 * Shows [page] of the project's PDF, fitted to the width of the area, with a vertical scrollbar if the page is
 * taller. The picture is rendered by [source] in the background at exactly the size it is drawn, one image pixel
 * per screen pixel. While it is not there, the area says so; if it fails, the reason is shown.
 */
@Composable
fun PageViewer(
    source: PageSource,
    page: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPixels = max(1, constraints.maxWidth)
        val picture by produceState<Picture>(Picture.Loading, source, page, widthPixels) {
            value = Picture.Loading
            value = fitWidth(source, page, widthPixels)
        }
        when (val shown = picture) {
            Picture.Loading -> Text("Loading…", Modifier.align(Alignment.Center))
            is Picture.Failed ->
                Text(
                    "The page cannot be shown: ${shown.reason}",
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error,
                )
            is Picture.Ready ->
                Image(
                    bitmap = shown.image,
                    contentDescription = "Page $page",
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    alignment = Alignment.TopStart,
                    contentScale = ContentScale.None,
                )
        }
    }
}

private suspend fun fitWidth(
    source: PageSource,
    page: Int,
    widthPixels: Int,
): Picture =
    try {
        val size = source.size(page)
        val scale = widthPixels / size.width
        val height = min(MAX_PICTURE_HEIGHT, max(1, ceil(size.height * scale).toInt()))
        Picture.Ready(source.render(page, scale, PixelRegion(0, 0, widthPixels, height)))
    } catch (e: CancellationException) {
        throw e
    } catch (
        // Whatever goes wrong with the PDF is shown in the viewer, not as a crash of the window.
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        Picture.Failed(e.message ?: e::class.simpleName ?: "unknown error")
    }
