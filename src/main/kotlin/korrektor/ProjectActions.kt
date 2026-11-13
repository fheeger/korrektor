package korrektor

/** What the user can do on the screen of the open project. */
class ProjectActions(
    val newProject: () -> Unit,
    val openProject: () -> Unit,
    val editMetadata: () -> Unit,
)
