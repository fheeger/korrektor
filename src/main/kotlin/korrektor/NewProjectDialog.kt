package korrektor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * The "New project" dialog: choose a PDF first, then the metadata and where the project goes.
 * The fields stay disabled until a PDF is chosen, and everything is disabled while the project is created.
 */
@Composable
fun NewProjectDialog(
    app: AppModel,
    pdfPicker: PdfPicker,
    folderPicker: FolderPicker,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val model = remember { NewProjectModel(app, scope) }
    Dialog(onDismissRequest = { if (!model.busy) app.closeNewProject() }) {
        Surface(modifier, shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp).width(520.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("New project", style = MaterialTheme.typography.headlineSmall)
                PdfRow(model, pdfPicker)
                MetadataFields(model, enabled = model.pdf != null && !model.busy)
                LocationRow(model, folderPicker)
                model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                ButtonRow(model, onCancel = app::closeNewProject)
            }
        }
    }
}

@Composable
private fun PdfRow(
    model: NewProjectModel,
    pdfPicker: PdfPicker,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { pdfPicker.pick()?.let(model::selectPdf) }, enabled = !model.busy) { Text("Choose PDF…") }
        Text(model.pdf?.fileName?.toString() ?: "No PDF chosen")
    }
}

@Composable
private fun LocationRow(
    model: NewProjectModel,
    folderPicker: FolderPicker,
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Location: ${model.location ?: "–"}", modifier = Modifier.weight(1f))
            TextButton(
                onClick = { folderPicker.pick()?.let(model::selectLocation) },
                enabled = model.pdf != null && !model.busy,
            ) { Text("Change…") }
        }
        model.projectFolder?.let { Text("Project folder: $it", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ButtonRow(
    model: NewProjectModel,
    onCancel: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (model.busy) CircularProgressIndicator(Modifier.width(24.dp))
        TextButton(onClick = onCancel, enabled = !model.busy) { Text("Cancel") }
        Button(onClick = { model.create() }, enabled = model.canCreate) { Text("Create") }
    }
}
