package korrektor.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * The small indicator of the save status (S07): "Saved", "Saving…" or "Not saved", in the error colour when the last
 * save failed. What it says is decided by `AutoSaver.status`; this only shows it. Test tag `save status`.
 */
@Composable
fun SaveIndicator(
    saver: AutoSaver,
    modifier: Modifier = Modifier,
) {
    val status = saver.status
    val failed = status == SaveStatus.NOT_SAVED && saver.error != null
    Text(
        status.text,
        modifier.testTag("save status"),
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
        style = MaterialTheme.typography.bodyMedium,
    )
}
