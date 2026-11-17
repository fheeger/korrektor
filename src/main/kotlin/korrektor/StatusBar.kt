package korrektor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import korrektor.editor.BookEditor
import korrektor.format.PageStatus
import kotlin.math.roundToInt

private const val FULL_PERCENT = 100

/**
 * How far the book is, for the status bar (S09): "57 of 165 corrected (35 %)". The percentage is rounded, but it says
 * 100 % only when every page is corrected and at least 1 % as soon as one is, so that it never claims more or less
 * than there is.
 */
fun progressText(
    corrected: Int,
    total: Int,
): String {
    val percent =
        when {
            total <= 0 || corrected <= 0 -> 0
            corrected >= total -> FULL_PERCENT
            else -> (FULL_PERCENT.toDouble() * corrected / total).roundToInt().coerceIn(1, FULL_PERCENT - 1)
        }
    return "$corrected of $total corrected ($percent %)"
}

/** How many pages of the first [total] are corrected. */
fun BookEditor.correctedPages(total: Int): Int =
    statuses.count { (page, status) ->
        page in 1..total &&
            status == PageStatus.CORRECTED
    }

/**
 * The bar between the header and the panels: the progress of the whole book. It reads the statuses of the editor, which
 * are in memory, so showing it reads no page. Test tag `progress`.
 */
@Composable
fun StatusBar(
    editor: BookEditor,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    val corrected = editor.correctedPages(pageCount)
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(progressText(corrected, pageCount), Modifier.testTag("progress"))
    }
}
