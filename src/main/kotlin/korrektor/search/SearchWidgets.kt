package korrektor.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import korrektor.editor.HistoricFonts

/** The shape of the fields and buttons of the panel. */
val FIELD_SHAPE = RoundedCornerShape(4.dp)

/** A small button that is on or off (an option, the scope): it says so to the screen reader as a checkbox does. */
@Composable
internal fun Toggle(
    text: String,
    on: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .border(1.dp, if (on) colors.primary else colors.outline, FIELD_SHAPE)
            .background(if (on) colors.primaryContainer else colors.surface, FIELD_SHAPE)
            .toggleable(on, role = Role.Checkbox, onValueChange = onChange)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(tag),
    ) {
        Text(text, fontSize = 13.sp)
    }
}

/** A one-line text field with a border and a hint that shows while it is empty. */
@Composable
internal fun TextBox(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value,
        onChange,
        modifier
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                FIELD_SHAPE,
            ).padding(horizontal = 8.dp, vertical = 6.dp),
        singleLine = true,
        textStyle =
            TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = HistoricFonts.family,
                fontSize = 15.sp,
            ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Box {
                if (value.text.isEmpty()) Text(hint, color = MaterialTheme.colorScheme.outline, fontSize = 15.sp)
                inner()
            }
        },
    )
}
