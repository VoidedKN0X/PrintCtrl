package com.vknxworks.printctrl.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.data.settings.ThemeChoice

/** One Ctrl / Signal palette. [CtrlColors.Dark] and [CtrlColors.Light] are the two the app uses. */
@Immutable
data class CtrlPalette(
    val isDark: Boolean,
    /** Accent for text and icons on this palette's backgrounds (fills always use [CtrlColors.Accent]). */
    val accentText: Color,
    val background: Color,
    /** Cards and sheets. */
    val surface: Color,
    val raised: Color,
    /** Card and sheet borders. */
    val outline: Color,
    /** Borders on controls: buttons, fields, the profile picture, the tab bar. */
    val controlOutline: Color,
    val divider: Color,
    val onSurface: Color,
    val muted: Color,
    /** Disabled, stopped, offline. */
    val stopped: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    /** Progress and meter tracks. */
    val track: Color,
    val switchOffTrack: Color,
    val switchOffKnob: Color,
    val pinEmpty: Color,
    /** Amber for warnings, pauses and low stock. */
    val warning: Color,
    /** Outline around very light filament swatches (white, natural), so they stay visible on the cards. */
    val lightSwatchEdge: Color,
)

/**
 * Ctrl / Signal: the look shared by every Ctrl app. Follows the device's dark or light setting, with one
 * accent. Change [CtrlColors.Accent] to recolour the whole app.
 */
object CtrlColors {
    /** The one accent colour, used for fills (hero cards, main buttons, selected tab, switches, PIN dots). */
    val Accent = Color(0xFFB48CFF)
    /** Content on an accent fill, in both palettes. */
    val OnAccent = Color(0xFF08080A)

    val Dark = CtrlPalette(
        isDark = true,
        accentText = Accent,
        background = Color(0xFF08080A),
        surface = Color(0xFF141417),
        raised = Color(0xFF1C1C21),
        outline = Color(0xFF232328),
        controlOutline = Color(0xFF2A2A2F),
        divider = Color(0xFF1E1E23),
        onSurface = Color(0xFFF4F4F2),
        muted = Color(0xFFA3A3AB),
        stopped = Color(0xFF8C8C94),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF08080A),
        errorContainer = Color(0xFF3A1A1C),
        onErrorContainer = Color(0xFFFFDAD8),
        track = Color(0xFF26262C),
        switchOffTrack = Color(0xFF2A2A30),
        switchOffKnob = Color(0xFF8C8C94),
        pinEmpty = Color(0xFF3A3A41),
        warning = Color(0xFFE8A33D),
        lightSwatchEdge = Color(0xFF2A2A2F),
    )

    /** "Signal Light". The accent stays the fill colour; text and icons use a deeper violet that reads on white. */
    val Light = CtrlPalette(
        isDark = false,
        accentText = Color(0xFF6A3BDB),
        background = Color(0xFFF5F5F1),
        surface = Color(0xFFFFFFFF),
        raised = Color(0xFFECECE7),
        outline = Color(0xFFDEDED7),
        controlOutline = Color(0xFFD2D2CA),
        divider = Color(0xFFE6E6E0),
        onSurface = Color(0xFF0B0B0D),
        muted = Color(0xFF5C5C64),
        stopped = Color(0xFF74747C),
        error = Color(0xFFD93636),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFBE4E4),
        onErrorContainer = Color(0xFF5C1010),
        track = Color(0xFFE2E2DC),
        switchOffTrack = Color(0xFFDCDCD5),
        switchOffKnob = Color(0xFF74747C),
        pinEmpty = Color(0xFFC9C9C1),
        warning = Color(0xFFB26A00),
        lightSwatchEdge = Color(0xFF9E9E96),
    )

    // Shorthands for the current palette (see [LocalCtrlColors]).
    val AccentText: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.accentText
    val Background: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.surface
    val Raised: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.raised
    val Outline: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.outline
    val ControlOutline: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.controlOutline
    val Divider: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.divider
    val OnSurface: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.onSurface
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.muted
    val Stopped: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.stopped
    val Error: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.error
    val Track: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.track
    val SwitchOff: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.switchOffTrack
    val SwitchOffKnob: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.switchOffKnob
    val PinEmpty: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.pinEmpty
    val Warning: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.warning
}

/** The Ctrl / Signal palette in use: [CtrlColors.Dark] or [CtrlColors.Light], following the device. */
val LocalCtrlColors = staticCompositionLocalOf { CtrlColors.Dark }

object CtrlShapes {
    val Card = RoundedCornerShape(22.dp)
    val Hero = RoundedCornerShape(26.dp)
    val Dialog = RoundedCornerShape(26.dp)
    /** Bottom sheets: rounded on top only. */
    val SheetTop = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val Control = RoundedCornerShape(14.dp)
    val Button = RoundedCornerShape(18.dp)
    val Field = RoundedCornerShape(16.dp)
    val Badge = RoundedCornerShape(10.dp)
    val Dot = RoundedCornerShape(2.dp)
}

/** The 1 dp hairline around cards, sheets and dialogs. */
val CardBorder: BorderStroke @Composable @ReadOnlyComposable get() = BorderStroke(1.dp, CtrlColors.Outline)

/** The 1 dp border around controls. */
val ControlBorder: BorderStroke @Composable @ReadOnlyComposable get() = BorderStroke(1.dp, CtrlColors.ControlOutline)

private fun CtrlPalette.scheme(): ColorScheme {
    // Material's "primary" is the accent as text and icons; components that fill with the accent use
    // CtrlColors.Accent directly (or primaryContainer).
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accentText,
        onPrimary = if (isDark) CtrlColors.OnAccent else Color.White,
        primaryContainer = CtrlColors.Accent,
        onPrimaryContainer = CtrlColors.OnAccent,
        inversePrimary = if (isDark) Color(0xFF6A3BDB) else CtrlColors.Accent,
        secondary = onSurface,
        onSecondary = background,
        secondaryContainer = track,
        onSecondaryContainer = onSurface,
        tertiary = onSurface,
        onTertiary = background,
        tertiaryContainer = raised,
        onTertiaryContainer = onSurface,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = raised,
        onSurfaceVariant = muted,
        surfaceTint = Color.Transparent,
        surfaceBright = raised,
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = if (isDark) Color(0xFF0F0F12) else Color(0xFFFAFAF7),
        surfaceContainer = surface,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = track,
        outline = controlOutline,
        outlineVariant = outline,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        inverseSurface = onSurface,
        inverseOnSurface = background,
        scrim = Color.Black,
    )
}

private val DarkScheme = CtrlColors.Dark.scheme()
private val LightScheme = CtrlColors.Light.scheme()

/** Space Grotesk: all UI text and headings (SIL OFL 1.1, bundled). */
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

/** JetBrains Mono: numbers, data readouts, section labels and the wordmark (SIL OFL 1.1, bundled). */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = SpaceGrotesk,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
)

private val AppTypography = Typography(
    displayLarge = style(56, 54, FontWeight.Bold, -0.04),
    displayMedium = style(48, 48, FontWeight.Bold, -0.04),
    displaySmall = style(40, 42, FontWeight.Bold, -0.035),
    headlineLarge = style(40, 42, FontWeight.Bold, -0.035),
    headlineMedium = style(30, 34, FontWeight.Bold, -0.03),
    headlineSmall = style(24, 30, FontWeight.Bold, -0.02),
    titleLarge = style(20, 26, FontWeight.SemiBold, -0.01),
    titleMedium = style(18, 24, FontWeight.SemiBold),
    titleSmall = style(16, 22, FontWeight.Medium),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 21, FontWeight.Normal),
    bodySmall = style(12, 17, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 14, FontWeight.Medium, 0.01),
)

/** Ctrl type that Material's scale has no slot for. Mono labels are written in capitals by [SectionLabel]-style callers. */
object CtrlType {
    /** "PRINT/CTRL" in the top bar. */
    val Wordmark = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em)
    /** Section headers and small data labels, in capitals. */
    val Label = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.16.em)
    val LabelStrong = Label.copy(fontWeight = FontWeight.SemiBold)
    /** Status words at the end of a row (PRINTING, IDLE). */
    val Status = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.em)
    /** Data lines: addresses, temperatures, times. */
    val Data = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal)
    /** A number that matters, inside a card. */
    val Readout = TextStyle(fontFamily = JetBrainsMono, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em)
    /** The giant number on a hero card. */
    val Hero = TextStyle(fontFamily = SpaceGrotesk, fontSize = 64.sp, lineHeight = 58.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.05).em)
    /** PIN pad digits. */
    val Key = TextStyle(fontFamily = JetBrainsMono, fontSize = 26.sp, fontWeight = FontWeight.Medium)
}

private val AppShapes = Shapes(
    // Text fields and small menus.
    extraSmall = CtrlShapes.Field,
    // Chips and small badges.
    small = CtrlShapes.Badge,
    // Controls, thumbnails, tiles.
    medium = CtrlShapes.Control,
    // Cards and grouped lists.
    large = CtrlShapes.Card,
    // Dialogs and bottom sheets.
    extraLarge = CtrlShapes.Dialog,
)

/** Horizontal page margin used everywhere. */
val Gutter = 20.dp

/**
 * Follows the device's dark or light setting; there's no theme choice in the app and never wallpaper
 * colours. [choice] and [dynamic] are still read from settings (and kept in backups) but change nothing.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun PrintCtrlTheme(
    choice: ThemeChoice = ThemeChoice.SYSTEM,
    dynamic: Boolean = false,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (dark) CtrlColors.Dark else CtrlColors.Light
    CompositionLocalProvider(LocalCtrlColors provides palette) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
