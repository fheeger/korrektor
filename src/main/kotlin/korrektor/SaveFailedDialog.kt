package korrektor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

/**
 * Shown when the window is to be closed and the changes could not be saved (S07): what went wrong, and three ways on.
 * Saving to another location copies the project to a folder the user chooses and opens the copy; closing without
 * saving loses the changes; keeping the app open leaves them in memory, where saving is tried again.
 */
@Composable
fun SaveFailedDialog(
    model: AppModel,
    folderPicker: FolderPicker,
) {
    Dialog(onDismissRequest = { model.closeProblem = null }) { SaveFailedContent(model, folderPicker) }
}

/** The content of [SaveFailedDialog], apart from its window, so that it can be tested. */
@Composable
fun SaveFailedContent(
    model: AppModel,
    folderPicker: FolderPicker,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val keepOpen = { model.closeProblem = null }
    Surface(modifier.testTag("save failed"), shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
        Column(Modifier.padding(24.dp).width(520.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your changes are not saved", style = MaterialTheme.typography.headlineSmall)
            Text(model.closeProblem.orEmpty(), color = MaterialTheme.colorScheme.error)
            Text(
                "They are still in the app, and saving is tried again. You can save the project to another location.",
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    model.closeProblem = null
                    model.exit()
                }) { Text("Close without saving") }
                OutlinedButton(onClick = keepOpen) { Text("Keep the app open") }
                Button(onClick = {
                    folderPicker.pick()?.let { parent -> scope.launch { model.saveElsewhere(parent) } }
                }) { Text("Save to another location…") }
            }
        }
    }
}
