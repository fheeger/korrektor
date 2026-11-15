package korrektor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.awt.Cursor

/** The width of the divider. */
private val DIVIDER_WIDTH = 8.dp

/**
 * Two panels side by side with a divider that can be dragged. The position is [layout]'s `splitFraction`, the share
 * of the width (without the divider) that [left] gets; a drag moves the divider in [layout] (which limits it) and
 * calls [onDragEnd] when the mouse is released. The panels keep their state while the divider moves.
 */
@Composable
fun SplitPane(
    layout: LayoutState,
    onDragEnd: () -> Unit,
    left: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    right: @Composable () -> Unit,
) {
    var totalWidth by remember { mutableIntStateOf(0) }
    val dividerPixels = with(LocalDensity.current) { DIVIDER_WIDTH.roundToPx() }
    val currentEnd by rememberUpdatedState(onDragEnd)
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()

    Row(modifier.onSizeChanged { totalWidth = it.width }) {
        Box(Modifier.weight(layout.splitFraction).fillMaxHeight().testTag("scan panel")) { left() }
        Box(
            Modifier
                .width(DIVIDER_WIDTH)
                .fillMaxHeight()
                .testTag("divider")
                .background(
                    if (hovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ).hoverable(hover)
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragEnd = { currentEnd() }) { change, dragAmount ->
                        change.consume()
                        val usable = (totalWidth - dividerPixels).coerceAtLeast(1)
                        layout.moveDivider(layout.splitFraction + dragAmount / usable)
                    }
                },
        )
        Box(Modifier.weight(1f - layout.splitFraction).fillMaxHeight().testTag("text panel")) { right() }
    }
}
