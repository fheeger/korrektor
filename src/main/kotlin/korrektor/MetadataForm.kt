package korrektor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The four metadata fields of a project, as a form. Used by the dialogs for creating and editing metadata. */
interface MetadataForm {
    val title: String
    val author: String
    val year: String
    val source: String

    fun updateTitle(text: String)

    fun updateAuthor(text: String)

    fun updateYear(text: String)

    fun updateSource(text: String)
}

/** Title (required), author, year and source: all free text, stored as typed. */
@Composable
fun MetadataFields(
    form: MetadataForm,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val fieldModifier = Modifier.fillMaxWidth()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = form.title,
            onValueChange = form::updateTitle,
            modifier = fieldModifier,
            enabled = enabled,
            label = { Text("Title (required)") },
            singleLine = true,
        )
        OutlinedTextField(
            value = form.author,
            onValueChange = form::updateAuthor,
            modifier = fieldModifier,
            enabled = enabled,
            label = { Text("Author") },
            singleLine = true,
        )
        OutlinedTextField(
            value = form.year,
            onValueChange = form::updateYear,
            modifier = fieldModifier,
            enabled = enabled,
            label = { Text("Year") },
            singleLine = true,
        )
        OutlinedTextField(
            value = form.source,
            onValueChange = form::updateSource,
            modifier = fieldModifier,
            enabled = enabled,
            label = { Text("Source (e.g. a link)") },
            singleLine = true,
        )
    }
}
