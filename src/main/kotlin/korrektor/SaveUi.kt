package korrektor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import korrektor.editor.AutoSaver

/**
 * Keeps the corrections saved while a project is open (S07): runs the timer of [saver] (after a pause, regularly while
 * typing), and asks it to save now when the shown page changes. Another project means another saver, so a fresh start.
 */
@Composable
fun KeepSaving(
    saver: AutoSaver?,
    shownPage: Int?,
) {
    LaunchedEffect(saver) { saver?.run() }
    LaunchedEffect(saver, shownPage) { saver?.requestFlush() }
}

/** The banner about a failed save: the reason, and that the changes are kept and saving is tried again. */
@Composable
fun SaveWarning(
    saver: AutoSaver?,
    modifier: Modifier = Modifier,
) {
    val reason = saver?.warning ?: return
    ErrorBanner(
        message = "Your changes could not be saved: $reason. They are kept, and saving is tried again.",
        onDismiss = saver::dismissWarning,
        modifier = modifier.testTag("save warning"),
    )
}

/** The dialogs that are open: new project, metadata, the shortcuts, and "your changes are not saved". */
@Composable
fun Dialogs(
    model: AppModel,
    folderPicker: FolderPicker,
    pdfPicker: PdfPicker,
) {
    if (model.newProjectOpen) NewProjectDialog(model, pdfPicker, folderPicker)
    if (model.editMetadataOpen) EditMetadataDialog(model)
    if (model.shortcutsOpen) ShortcutListDialog { model.shortcutsOpen = false }
    if (model.closeProblem != null) SaveFailedDialog(model, folderPicker)
}
