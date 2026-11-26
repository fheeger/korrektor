package korrektor.characters

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuScope

/** The title of the menu with the special characters. */
const val INSERT_MENU = "Insert"

/** What an entry of the Insert menu says: the character, what it is, and its keys. */
val SpecialCharacter.menuText: String get() = "$text   $name   ($shortcut)"

/** The submenus of the Insert menu, one per group; the entries are grey while no text field takes characters. */
@Composable
fun MenuScope.CharacterMenus(input: CharacterInput) {
    for (group in CharacterGroup.entries) {
        Menu(group.title) {
            for (character in SpecialCharacters.all.filter { it.group == group }) {
                Item(character.menuText, enabled = input.ready, onClick = { input.insert(character) })
            }
        }
    }
}

/** [content] with [input] for its text fields, and over its lower edge the hint that the second key is waited for. */
@Composable
fun CharacterArea(
    input: CharacterInput,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalCharacterInput provides input) {
        Box(modifier) {
            content()
            if (input.waiting) LeaderHint(Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun LeaderHint(modifier: Modifier = Modifier) {
    Surface(
        modifier.padding(16.dp).testTag("leader hint"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.inverseSurface,
    ) {
        Text(
            "${LEADER.text}: press the key of a character (F1 lists them), Esc cancels",
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
