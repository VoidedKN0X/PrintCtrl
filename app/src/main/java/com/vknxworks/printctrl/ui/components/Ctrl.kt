package com.vknxworks.printctrl.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.servers.ConnState
import com.vknxworks.printctrl.data.servers.ServerKind
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.nav.YouRoute
import com.vknxworks.printctrl.ui.theme.CardBorder
import com.vknxworks.printctrl.ui.theme.ControlBorder
import com.vknxworks.printctrl.ui.theme.CtrlColors
import com.vknxworks.printctrl.ui.theme.CtrlShapes
import com.vknxworks.printctrl.ui.theme.CtrlType
import com.vknxworks.printctrl.ui.theme.Gutter
import com.vknxworks.printctrl.ui.theme.SpaceGrotesk

// Ctrl / Signal building blocks shared by every screen.

/**
 * Space taken at the bottom of the screen by the floating tab bar. Scrolling screens add it to their
 * bottom content padding, so their last items can scroll clear of the bar.
 */
val LocalBottomClearance = compositionLocalOf { 0.dp }

// ---------- Labels ----------

/** Mono section label in capitals: "TEMPERATURES", "PRIVACY". */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = CtrlColors.Muted) {
    Text(text.uppercase(), style = CtrlType.Label, color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Mono status word with the 8 dp square dot after it, as at the end of a list row. */
@Composable
fun StatusTag(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = CtrlType.Status, color = color, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        StatusDot(color)
    }
}

/** The 8 dp square status dot. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CtrlShapes.Dot).background(color))
}

/** "PRINT/CTRL": the wordmark, with the slash in the accent colour. */
@Composable
fun Wordmark(name: String = "PRINT", modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append(name)
            withStyle(SpanStyle(color = CtrlColors.Accent)) { append("/") }
            append("CTRL")
        },
        style = CtrlType.Wordmark,
        color = CtrlColors.OnSurface,
        modifier = modifier,
    )
}

// ---------- Top bars ----------

/** Main-tab top bar: the wordmark on the left, optional actions and the profile picture on the right. */
@Composable
fun CtrlTopBar(modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Wordmark()
        Spacer(Modifier.weight(1f))
        actions()
        Spacer(Modifier.width(6.dp))
        ProfileButton()
    }
}

/** The square 44 dp button used in top bars (back, close, actions). */
@Composable
fun CtrlSquareButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = CtrlColors.OnSurface,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CtrlShapes.Control)
            .background(CtrlColors.Surface)
            .border(ControlBorder, CtrlShapes.Control)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, Modifier.size(20.dp), tint = tint) }
}

/** Sub-screen top bar: square back button and the title. */
@Composable
fun CtrlSubBar(
    title: String,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    onBack: () -> Unit = {},
    containerColor: Color = CtrlColors.Background,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().background(containerColor).statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            CtrlSquareButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onBack)
            Spacer(Modifier.width(14.dp))
        } else {
            Spacer(Modifier.width(4.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = CtrlColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/**
 * The profile picture; tapping it opens the You page (servers, Settings, About). There's no settings
 * tab; this is the way in. A small accent square shows while PrintCtrl is connected.
 */
@Composable
fun ProfileButton() {
    val nav = LocalNavigator.current
    val c = LocalContext.current.container
    val setup by c.servers.setup.collectAsStateWithLifecycle()
    val printers by c.printers.state.collectAsStateWithLifecycle()
    val spoolman by c.connections.state(ServerKind.SPOOLMAN).collectAsStateWithLifecycle()
    val connected = if (setup?.bambuddy != null) printers.loaded && printers.error == null
    else spoolman is ConnState.Ready
    val label = stringResource(R.string.you_title)
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CtrlShapes.Control)
                .background(CtrlColors.Surface)
                .border(ControlBorder, CtrlShapes.Control)
                .clickable(role = Role.Button, onClickLabel = label) { nav.navigate(YouRoute) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Person, label, Modifier.size(22.dp), tint = CtrlColors.OnSurface)
        }
        if (connected) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CtrlColors.Background)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(CtrlColors.Accent),
            )
        }
    }
}

// ---------- Cards ----------

/** Secondary card: surface fill, 1 dp border, 22 dp corners. */
@Composable
fun CtrlCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = CtrlShapes.Card,
    color: Color = CtrlColors.Surface,
    contentColor: Color = CtrlColors.OnSurface,
    border: BorderStroke? = CardBorder,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(10.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = shape,
        color = color,
        contentColor = contentColor,
        border = border,
        modifier = modifier.clip(shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = verticalArrangement, content = content)
    }
}

/**
 * The one most important thing on a screen: accent fill with near-black content, a mono label row on
 * top and (usually) a giant number below.
 */
@Composable
fun CtrlHeroCard(
    label: String,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    CtrlCard(
        modifier = modifier,
        onClick = onClick,
        shape = CtrlShapes.Hero,
        color = CtrlColors.Accent,
        contentColor = CtrlColors.OnAccent,
        border = null,
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label.uppercase(),
                style = CtrlType.LabelStrong,
                color = CtrlColors.OnAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailingLabel != null) {
                Spacer(Modifier.width(10.dp))
                Text(trailingLabel.uppercase(), style = CtrlType.LabelStrong, color = CtrlColors.OnAccent, maxLines = 1)
            }
        }
        content()
    }
}

/** Giant readout: "42%" with a smaller unit. */
@Composable
fun HeroNumber(value: String, unit: String? = null, color: Color = CtrlColors.OnAccent, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append(value)
            if (unit != null) withStyle(SpanStyle(fontSize = 28.sp, letterSpacing = 0.sp)) { append(unit) }
        },
        style = CtrlType.Hero,
        color = color,
        modifier = modifier,
    )
}

// ---------- Lists ----------

/**
 * Lays its children out top to bottom with a 1 dp divider between them, like the rows of a grouped
 * card. Children that take no space get no divider.
 */
@Composable
fun DividedColumn(modifier: Modifier = Modifier, color: Color = CtrlColors.Outline, content: @Composable () -> Unit) {
    // Divider positions, worked out while measuring. Only the draw phase reads them, so a change just redraws.
    var lines by remember { mutableStateOf(emptyList<Int>()) }
    Layout(
        content = content,
        modifier = modifier.drawWithContent {
            drawContent()
            val stroke = 1.dp.toPx()
            lines.forEach { y -> drawRect(color, topLeft = Offset(0f, y.toFloat()), size = Size(size.width, stroke)) }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = placeables.sumOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
        val tops = IntArray(placeables.size)
        val ys = mutableListOf<Int>()
        var y = 0
        placeables.forEachIndexed { i, p ->
            tops[i] = y
            if (p.height > 0 && y > 0) ys += y
            y += p.height
        }
        if (ys != lines) lines = ys
        layout(width, height) {
            placeables.forEachIndexed { i, p -> p.placeRelative(0, tops[i]) }
        }
    }
}

// ---------- Controls ----------

/** Rounded-rect switch, 52 × 32 dp. Announced as a switch. */
@Composable
fun CtrlSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val track by animateColorAsState(if (checked) CtrlColors.Accent else CtrlColors.SwitchOff, label = "switch-track")
    val knob by animateColorAsState(if (checked) CtrlColors.OnAccent else CtrlColors.Stopped, label = "switch-knob")
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, label = "switch-knob-x")
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(track)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                } else {
                    Modifier
                },
            )
            .padding(4.dp),
    ) {
        Box(Modifier.offset(x = x).size(24.dp).clip(RoundedCornerShape(7.dp)).background(knob))
    }
}

/** 6 dp progress bar with a 3 dp radius; light fill by default, accent for the main number on a screen. */
@Composable
fun CtrlProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = CtrlColors.OnSurface,
    trackColor: Color = CtrlColors.Track,
    height: Dp = 6.dp,
) {
    val value = progress().coerceIn(0f, 1f)
    Canvas(modifier.height(height).progressSemantics(value)) {
        val r = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(trackColor, cornerRadius = r)
        if (value > 0f) drawRoundRect(color, size = Size(size.width * value, size.height), cornerRadius = r)
    }
}

@Composable
fun ctrlFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CtrlColors.Surface,
    unfocusedContainerColor = CtrlColors.Surface,
    disabledContainerColor = CtrlColors.Surface,
    errorContainerColor = CtrlColors.Surface,
    focusedBorderColor = CtrlColors.Accent,
    unfocusedBorderColor = CtrlColors.ControlOutline,
    disabledBorderColor = CtrlColors.Outline,
    cursorColor = CtrlColors.Accent,
    focusedLabelColor = CtrlColors.Accent,
    unfocusedLabelColor = CtrlColors.Muted,
)

/** Text field: surface fill, 1 dp border, 16 dp corners; accent border and cursor while focused. */
@Composable
fun CtrlTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = CtrlShapes.Field,
        colors = ctrlFieldColors(),
    )
}

/** [CtrlTextField] for a [TextFieldValue] (keeps the selection). */
@Composable
fun CtrlTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = label,
        placeholder = placeholder,
        trailingIcon = trailingIcon,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
        shape = CtrlShapes.Field,
        colors = ctrlFieldColors(),
    )
}

/** Dialog: surface fill, 26 dp corners, 1 dp border. */
@Composable
fun CtrlAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.border(CardBorder, CtrlShapes.Dialog),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = CtrlShapes.Dialog,
        containerColor = CtrlColors.Surface,
        titleContentColor = CtrlColors.OnSurface,
        textContentColor = CtrlColors.Muted,
        tonalElevation = 0.dp,
    )
}

// ---------- Bottom navigation ----------

data class TabItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * Floating tab bar: 64 dp tall, 22 dp corners, translucent surface with a 1 dp border. The selected
 * tab is an accent block with its icon and name; the others are icons only.
 */
@Composable
fun CtrlBottomBar(
    items: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(CtrlColors.Surface.copy(alpha = 0.92f))
            .border(ControlBorder, shape)
            .padding(horizontal = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, item ->
            val selected = i == selectedIndex
            val itemShape = RoundedCornerShape(16.dp)
            Row(
                Modifier
                    .height(48.dp)
                    .widthIn(min = 48.dp)
                    .clip(itemShape)
                    .background(if (selected) CtrlColors.Accent else Color.Transparent)
                    .selectable(
                        selected = selected,
                        onClick = {
                            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        },
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                    )
                    .animateContentSize()
                    .padding(horizontal = if (selected) 18.dp else 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (selected) item.selectedIcon else item.icon,
                    contentDescription = if (selected) null else item.label,
                    tint = if (selected) CtrlColors.OnAccent else CtrlColors.Muted,
                    modifier = Modifier.size(20.dp),
                )
                if (selected) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        item.label,
                        style = TextStyle(fontFamily = SpaceGrotesk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = CtrlColors.OnAccent,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
