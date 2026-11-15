package korrektor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import korrektor.format.Metadata
import korrektor.format.Project

/** The open project. For now it shows the project's metadata; the pages come with later stories. */
@Composable
fun ProjectScreen(
    project: Project,
    metadata: Metadata?,
    actions: ProjectActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(displayTitle(project, metadata), style = MaterialTheme.typography.headlineMedium)
        if (metadata != null) {
            listOf("Author" to metadata.author, "Year" to metadata.year, "Source" to metadata.source)
                .filter { it.second.isNotEmpty() }
                .forEach { (label, value) -> Text("$label: $value") }
        }
        Text("Pages: ${project.pages.size}")
        Text(project.folder.toString(), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = actions.editMetadata) { Text("Edit metadata…") }
            Button(onClick = actions.newProject) { Text("New project…") }
            Button(onClick = actions.openProject) { Text("Open another project…") }
        }
    }
}
