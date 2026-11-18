package korrektor.search

import korrektor.AppModel
import korrektor.closeProject
import korrektor.editor.withHistoryProject
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull

/** The search model belongs to the open project (T084): made when it is opened, gone when it is closed. */
class SearchWiringTest {
    @Test
    fun everyOpenProjectHasItsOwnSearchAndItGoesWithTheProject() =
        withHistoryProject(pages = 2) { project ->
            val model = AppModel()
            assertNull(model.search)
            model.openProject(project.folder)
            val first = assertNotNull(model.search)
            first.change(SearchQuery("Seite"))
            model.closeProject()
            assertNull(model.search)
            model.openProject(project.folder)
            assertNotSame(first, model.search, "a new project starts with a new search")
            assertNotNull(model.search)
            model.closeProject()
        }
}
