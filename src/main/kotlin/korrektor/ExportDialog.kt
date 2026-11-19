package korrektor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import korrektor.editor.currentLayer
import korrektor.export.BookExport
import korrektor.export.ExportBook
import korrektor.export.ExportFormat
import korrektor.export.ExportModel
import kotlinx.coroutines.Dispatchers

/** Opens the export dialog if a project is open. */
fun AppModel.openExport() {
    if (project != null && editor != null) exportOpen = true
}

fun AppModel.closeExport() {
    exportOpen = false
}

/**
 * The "Export" dialog (S11): the format, the pages, the options, and a note about pages that are not checked yet.
 * "Export…" asks where the file goes and then writes it in the background; while that runs, "Cancel" stops it.
 */
@Composable
fun ExportDialog(
    app: AppModel,
    picker: ExportPicker,
    modifier: Modifier = Modifier,
) {
    val project = checkNotNull(app.project)
    val editor = checkNotNull(app.editor)
    val scope = rememberCoroutineScope()
    val model =
        remember {
            ExportModel(
                ExportBook(project.pages.size, app.metadata) { editor.statuses },
                app.settings,
                BookExport(editor::currentLayer, Dispatchers.IO),
                scope,
            ) {
                app.notice = it
                app.closeExport()
            }
        }
    Dialog(onDismissRequest = { if (!model.busy) app.closeExport() }) {
        Surface(modifier.testTag("export dialog"), shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp).width(520.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Export", style = MaterialTheme.typography.headlineSmall)
                FormatRow(model)
                RangeRow(model)
                OptionRows(model)
                model.uncheckedNote?.let { Text(it, Modifier.testTag("export note")) }
                if (model.busy) Progress(model)
                model.error?.let { Text(it, Modifier.testTag("export error"), color = MaterialTheme.colorScheme.error) }
                Buttons(model, picker, onClose = app::closeExport)
            }
        }
    }
}

@Composable
private fun FormatRow(model: ExportModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        for (format in ExportFormat.entries) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = model.format == format,
                    onClick = { model.selectFormat(format) },
                    modifier = Modifier.testTag("export format ${format.name}"),
                    enabled = !model.busy,
                )
                Text(format.label)
            }
        }
    }
}

@Composable
private fun RangeRow(model: ExportModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Pages")
            OutlinedTextField(
                value = model.from,
                onValueChange = model::updateFrom,
                modifier = Modifier.width(100.dp).testTag("export from"),
                enabled = !model.busy,
                label = { Text("From") },
                singleLine = true,
                isError = model.rangeProblem != null,
            )
            OutlinedTextField(
                value = model.to,
                onValueChange = model::updateTo,
                modifier = Modifier.width(100.dp).testTag("export to"),
                enabled = !model.busy,
                label = { Text("To") },
                singleLine = true,
                isError = model.rangeProblem != null,
            )
        }
        model.rangeProblem?.let {
            Text(it, Modifier.testTag("export range problem"), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun OptionRows(model: ExportModel) {
    Column {
        OptionRow(
            "Page markers",
            "[Page 12] before each page",
            model.options.pageMarkers,
            !model.busy,
        ) { model.changeOptions(model.options.copy(pageMarkers = it)) }
        OptionRow(
            "Header",
            "title, author, year and source at the top",
            model.options.header,
            !model.busy,
        ) { model.changeOptions(model.options.copy(header = it)) }
        OptionRow(
            "Keep the printed line breaks",
            "otherwise the lines of a page are joined",
            model.options.keepLineBreaks,
            !model.busy,
        ) { model.changeOptions(model.options.copy(keepLineBreaks = it)) }
    }
}

@Composable
private fun OptionRow(
    label: String,
    hint: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, Modifier.testTag("export option $label"), enabled = enabled)
        Text("$label ($hint)")
    }
}

@Composable
private fun Progress(model: ExportModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Exporting page ${model.done} of ${model.pagesInRange}", Modifier.testTag("export progress"))
        LinearProgressIndicator(
            progress = { model.done / model.pagesInRange.coerceAtLeast(1).toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** While the export runs, "Cancel" stops it; otherwise it closes the dialog. "Export…" asks for the file first. */
@Composable
private fun Buttons(
    model: ExportModel,
    picker: ExportPicker,
    onClose: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = if (model.busy) model::cancel else onClose, modifier = Modifier.testTag("export cancel")) {
            Text("Cancel")
        }
        Button(
            onClick = {
                val name = model.suggestedName { NewProject.folderName(it).removeSuffix(".korrektur") }
                picker.pick(name, model.format)?.let(model::start)
            },
            enabled = model.canExport,
            modifier = Modifier.testTag("export start"),
        ) { Text("Export…") }
    }
}
