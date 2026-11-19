package korrektor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import korrektor.format.Metadata
import korrektor.format.Project

/**
 * The open project: a compact header (the title and the three buttons in a row, the metadata, the number of pages and
 * the folder in a line), and below it [content], which gets all the room that is left: the scan and the text.
 */
@Composable
fun ProjectScreen(
    project: Project,
    metadata: Metadata?,
    actions: ProjectActions,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit = {},
) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(displayTitle(project, metadata), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            Button(onClick = actions.editMetadata) { Text("Edit metadata…") }
            Button(onClick = actions.export) { Text("Export…") }
            Button(onClick = actions.newProject) { Text("New project…") }
            Button(onClick = actions.openProject) { Text("Open another project…") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (metadata != null) {
                listOf("Author" to metadata.author, "Year" to metadata.year, "Source" to metadata.source)
                    .filter { it.second.isNotEmpty() }
                    .forEach { (label, value) -> Text("$label: $value") }
            }
            Text("Pages: ${project.pages.size}")
            Text(project.folder.toString(), style = MaterialTheme.typography.bodySmall)
        }
        content(Modifier.weight(1f).fillMaxWidth())
    }
}
