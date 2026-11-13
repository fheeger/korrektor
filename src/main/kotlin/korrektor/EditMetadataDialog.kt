package korrektor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** The "Edit metadata" dialog for the open project. Cancel and closing it change nothing. */
@Composable
fun EditMetadataDialog(
    app: AppModel,
    modifier: Modifier = Modifier,
) {
    val model = remember { EditMetadataModel(app, app.metadata) }
    Dialog(onDismissRequest = app::closeEditMetadata) {
        Surface(modifier, shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp).width(520.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Edit metadata", style = MaterialTheme.typography.headlineSmall)
                MetadataFields(model, enabled = true)
                model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = app::closeEditMetadata) { Text("Cancel") }
                    Button(onClick = model::save, enabled = model.canSave) { Text("Save") }
                }
            }
        }
    }
}
