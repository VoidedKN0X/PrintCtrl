package com.vknxworks.printctrl.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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

/**
 * Ctrl / Signal: the look shared by every Ctrl app. Dark only, near-black graphite, one accent.
 * Change [CtrlColors.Accent] to recolour the whole app.
 */
object CtrlColors {
    /** The one accent colour. Everything accent-coloured in the app reads it from here. */
    val Accent = Color(0xFFB48CFF)
    val OnAccent = Color(0xFF08080A)

    val Background = Color(0xFF08080A)
    val Surface = Color(0xFF141417)
    val Raised = Color(0xFF1C1C21)
    /** Card and sheet borders. */
    val Outline = Color(0xFF232328)
    /** Borders on controls: buttons, fields, the profile picture, the tab bar. */
    val ControlOutline = Color(0xFF2A2A2F)
    val Divider = Color(0xFF1E1E23)
    val OnSurface = Color(0xFFF4F4F2)
    val Muted = Color(0xFFA3A3AB)
    /** Disabled, stopped, offline. */
    val Stopped = Color(0xFF8C8C94)
    val Error = Color(0xFFFF6B6B)
    val Track = Color(0xFF26262C)
    val SwitchOff = Color(0xFF2A2A30)
    val PinEmpty = Color(0xFF3A3A41)
}

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
val CardBorder = BorderStroke(1.dp, CtrlColors.Outline)

/** The 1 dp border around controls. */
val ControlBorder = BorderStroke(1.dp, CtrlColors.ControlOutline)

private val Dark = darkColorScheme(
    primary = CtrlColors.Accent,
    onPrimary = CtrlColors.OnAccent,
    primaryContainer = CtrlColors.Accent,
    onPrimaryContainer = CtrlColors.OnAccent,
    inversePrimary = CtrlColors.Accent,
    secondary = CtrlColors.OnSurface,
    onSecondary = CtrlColors.OnAccent,
    secondaryContainer = CtrlColors.Track,
    onSecondaryContainer = CtrlColors.OnSurface,
    tertiary = CtrlColors.OnSurface,
    onTertiary = CtrlColors.OnAccent,
    tertiaryContainer = CtrlColors.Raised,
    onTertiaryContainer = CtrlColors.OnSurface,
    background = CtrlColors.Background,
    onBackground = CtrlColors.OnSurface,
    surface = CtrlColors.Surface,
    onSurface = CtrlColors.OnSurface,
    surfaceVariant = CtrlColors.Raised,
    onSurfaceVariant = CtrlColors.Muted,
    surfaceTint = Color.Transparent,
    surfaceBright = CtrlColors.Raised,
    surfaceDim = CtrlColors.Background,
    surfaceContainerLowest = CtrlColors.Background,
    surfaceContainerLow = Color(0xFF0F0F12),
    surfaceContainer = CtrlColors.Surface,
    surfaceContainerHigh = CtrlColors.Raised,
    surfaceContainerHighest = CtrlColors.Track,
    outline = CtrlColors.ControlOutline,
    outlineVariant = CtrlColors.Outline,
    error = CtrlColors.Error,
    onError = CtrlColors.OnAccent,
    errorContainer = Color(0xFF3A1A1C),
    onErrorContainer = Color(0xFFFFDAD8),
    inverseSurface = CtrlColors.OnSurface,
    inverseOnSurface = CtrlColors.Background,
    scrim = Color.Black,
)

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
 * Dark only. [choice] and [dynamic] are still read from settings (and kept in backups) but no longer
 * change anything: Ctrl / Signal has one dark look and never takes colours from the wallpaper.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun PrintCtrlTheme(choice: ThemeChoice = ThemeChoice.DARK, dynamic: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, typography = AppTypography, shapes = AppShapes, content = content)
}
