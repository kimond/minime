package app.minime.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.minime.launcher.data.AppEntry
import app.minime.launcher.data.HomeAlignment

/** Shared look for home-screen widget cards: near-black, thin border, rounded. */
fun Modifier.widgetCard(): Modifier = this
    .fillMaxWidth()
    .background(Color(0xFF0C0C0C), RoundedCornerShape(16.dp))
    .border(1.dp, Color(0xFF1C1C1C), RoundedCornerShape(16.dp))

class MenuAction(val label: String, val onClick: () -> Unit)

val HomeAlignment.horizontal: Alignment.Horizontal
    get() = when (this) {
        HomeAlignment.START -> Alignment.Start
        HomeAlignment.CENTER -> Alignment.CenterHorizontally
        HomeAlignment.END -> Alignment.End
    }

val HomeAlignment.textAlign: TextAlign
    get() = when (this) {
        HomeAlignment.START -> TextAlign.Start
        HomeAlignment.CENTER -> TextAlign.Center
        HomeAlignment.END -> TextAlign.End
    }

/**
 * A text-only app entry: tap to open, long-press for a context menu.
 * [trailing] (e.g. today's screen time) is shown at the row's end when [trailingAtEnd],
 * otherwise right after the label (for centred / right-aligned home layouts).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppItem(
    app: AppEntry,
    style: TextStyle,
    onClick: () -> Unit,
    menuActions: () -> List<MenuAction>,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    trailingAtEnd: Boolean = true,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuOpen = true
                },
            )
            .padding(vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = (if (trailing != null && trailingAtEnd) Modifier.fillMaxWidth() else Modifier)
                .alpha(if (pressed) 0.5f else 1f),
        ) {
            Text(
                text = buildAnnotatedString {
                    append(app.label)
                    if (app.isWorkProfile) withStyle(SpanStyle(color = dim)) { append(" · work") }
                },
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // At the end: the label takes all remaining width so every time lines up on the right edge.
                modifier = if (trailing != null && trailingAtEnd) Modifier.weight(1f) else Modifier,
            )
            if (trailing != null) {
                Spacer(Modifier.width(14.dp))
                Text(
                    trailing,
                    textAlign = TextAlign.End,
                    style = TextStyle(
                        color = Color(0xFF6B6B6B),
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    maxLines = 1,
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            menuActions().forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        menuOpen = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

@Composable
fun RenameDialog(app: AppEntry, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember(app.key) { mutableStateOf(app.label) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text(app.originalLabel) },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Leave empty to restore “${app.originalLabel}”.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Fires [onSwipeLeft]/[onSwipeRight] once a horizontal drag passes a distance threshold. */
suspend fun PointerInputScope.detectHorizontalSwipe(onSwipeLeft: () -> Unit, onSwipeRight: () -> Unit) {
    val threshold = 80.dp.toPx()
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            when {
                total < -threshold -> onSwipeLeft()
                total > threshold -> onSwipeRight()
            }
        },
        onHorizontalDrag = { change, dx ->
            change.consume()
            total += dx
        },
    )
}

/** Fires [onSwipeUp]/[onSwipeDown] once a vertical drag passes a distance threshold. */
suspend fun PointerInputScope.detectVerticalSwipe(onSwipeUp: () -> Unit, onSwipeDown: () -> Unit) {
    val threshold = 80.dp.toPx()
    var total = 0f
    detectVerticalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            when {
                total < -threshold -> onSwipeUp()
                total > threshold -> onSwipeDown()
            }
        },
        onVerticalDrag = { change, dy ->
            change.consume()
            total += dy
        },
    )
}
