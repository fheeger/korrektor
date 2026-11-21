package korrektor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** The About dialog (Help menu): the name, version, license and repository of the app. Escape and "Close" close it. */
@Composable
fun AboutDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) { About(onClose) }
}

/** The content of the dialog, apart from the window, so that it can be tested. */
@Composable
fun About(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.testTag("about"), shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
        Column(Modifier.padding(24.dp).width(360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(APP_NAME, style = MaterialTheme.typography.headlineSmall)
            Text("Version ${AppInfo.version}", Modifier.testTag("about version"))
            Text("Turns historical PDFs into clean text by correcting their OCR.")
            Text("License: ${AppInfo.LICENSE}", Modifier.testTag("about license"))
            val links = LocalUriHandler.current
            Text(
                AppInfo.REPOSITORY,
                Modifier.testTag("about repository").clickable { links.openUri(AppInfo.REPOSITORY) },
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
    }
}
