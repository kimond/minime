package app.minime.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.minime.launcher.LauncherViewModel
import app.minime.launcher.data.MAX_SHORTCUTS
import app.minime.launcher.data.Shortcut

/** Icons a shortcut can use, keyed by the name stored in settings. Order = picker order. */
val ShortcutIcons: List<Pair<String, ImageVector>> = listOf(
    "notes" to Icons.Outlined.Edit,
    "math" to Icons.Outlined.Calculate,
    "calendar" to Icons.Outlined.CalendarMonth,
    "timer" to Icons.Outlined.Timer,
    "heart" to Icons.Outlined.FavoriteBorder,
    "alarm" to Icons.Outlined.Alarm,
    "fitness" to Icons.Outlined.FitnessCenter,
    "phone" to Icons.Outlined.Phone,
    "message" to Icons.Outlined.Sms,
    "mail" to Icons.Outlined.Email,
    "camera" to Icons.Outlined.CameraAlt,
    "photos" to Icons.Outlined.PhotoLibrary,
    "map" to Icons.Outlined.Map,
    "car" to Icons.Outlined.DirectionsCar,
    "music" to Icons.Outlined.MusicNote,
    "headphones" to Icons.Outlined.Headphones,
    "podcast" to Icons.Outlined.Podcasts,
    "web" to Icons.Outlined.Public,
    "book" to Icons.Outlined.Book,
    "idea" to Icons.Outlined.Lightbulb,
    "spa" to Icons.Outlined.Spa,
    "code" to Icons.Outlined.Code,
    "translate" to Icons.Outlined.Translate,
    "cart" to Icons.Outlined.ShoppingCart,
    "wallet" to Icons.Outlined.AccountBalanceWallet,
    "settings" to Icons.Outlined.Settings,
)

private val iconsByName = ShortcutIcons.toMap()

fun shortcutIcon(name: String): ImageVector = iconsByName[name] ?: Icons.Outlined.Circle

private val RingColor = Color(0xFF2E2E2E)
private val IconColor = Color(0xFFBDBDBD)
private val LabelColor = Color(0xFF6B6B6B)

/** Horizontal row of round icon buttons. Tap opens the app; long-press to edit. */
@Composable
fun ShortcutBar(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val shortcuts by vm.shortcuts.collectAsStateWithLifecycle()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        shortcuts.forEachIndexed { index, (shortcut, app) ->
            ShortcutButton(
                shortcut = shortcut,
                onClick = { if (app != null) vm.launch(app) else vm.editShortcut(shortcut) },
                menuActions = {
                    listOfNotNull(
                        MenuAction("Edit") { vm.editShortcut(shortcut) },
                        MenuAction("Move left") { vm.moveShortcut(shortcut.id, -1) }.takeIf { index > 0 },
                        MenuAction("Move right") { vm.moveShortcut(shortcut.id, 1) }.takeIf { index < shortcuts.lastIndex },
                        MenuAction("Remove") { vm.removeShortcut(shortcut.id) },
                        MenuAction("Add shortcut") { vm.newShortcut() }.takeIf { shortcuts.size < MAX_SHORTCUTS },
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutButton(shortcut: Shortcut, onClick: () -> Unit, menuActions: () -> List<MenuAction>) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(if (pressed) 0.5f else 1f)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                ),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, RingColor, CircleShape),
            ) {
                Icon(shortcutIcon(shortcut.icon), contentDescription = shortcut.label, tint = IconColor, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                shortcut.label.uppercase(),
                color = LabelColor,
                fontSize = 8.sp,
                letterSpacing = 1.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
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
