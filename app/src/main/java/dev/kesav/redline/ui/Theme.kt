package dev.kesav.redline.ui

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The page is a cool neutral and the cards on it are white. The first version printed
// everything on one warm off-white, which read as a single sheet of text with nothing to
// separate an action from a paragraph: every screen looked like a form. Cards now sit a
// step above the page, so the eye finds the things that can be tapped before the prose.
private val Page = Color(0xFFF1F2F5)
private val Card = Color(0xFFFFFFFF)
private val Ink = Color(0xFF111318)
private val InkMuted = Color(0xFF585D6B)    // 6.1:1 on Page, clears AA at label sizes
private val Hairline = Color(0xFFDFE1E7)
private val Red = Color(0xFFCC2B22)
private val RedTint = Color(0xFFFCE8E6)
private val Amber = Color(0xFFA85A06)       // 4.9:1 on Card
private val AmberTint = Color(0xFFFDF1E1)

private val PageDark = Color(0xFF0C0E12)
private val CardDark = Color(0xFF171A20)
private val CardDarkHigh = Color(0xFF20242B)
private val Paper = Color(0xFFF2F3F5)
private val PaperMuted = Color(0xFFA4A9B5)
private val HairlineDark = Color(0xFF2C3038)
private val RedDark = Color(0xFFFF6B5E)
private val RedTintDark = Color(0xFF3A1B19)
private val AmberDark = Color(0xFFF2A541)
private val AmberTintDark = Color(0xFF362612)

// Every role is set. Any role left out falls back to Material's baseline palette, which
// is violet, and that is how the first bottom sheet this app showed came up lilac.
private val Light = lightColorScheme(
    primary = Red,
    onPrimary = Color.White,
    primaryContainer = RedTint,
    onPrimaryContainer = Color(0xFF7A140F),
    // Secondary is what progress tracks, switches and filter chips reach for. Left unset
    // it was the baseline violet, and the first progress bar drew a lilac track.
    secondary = InkMuted,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7E9EE),
    onSecondaryContainer = Ink,
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = AmberTint,
    onTertiaryContainer = Color(0xFF5C3100),
    background = Page,
    onBackground = Ink,
    surface = Page,
    onSurface = Ink,
    surfaceVariant = Page,
    onSurfaceVariant = InkMuted,
    outline = InkMuted,
    outlineVariant = Hairline,
    surfaceContainerLowest = Card,
    surfaceContainerLow = Card,
    surfaceContainer = Card,
    surfaceContainerHigh = Card,
    surfaceContainerHighest = Color(0xFFE7E9EE),
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    inversePrimary = RedDark,
    scrim = Ink,
)

private val Dark = darkColorScheme(
    primary = RedDark,
    onPrimary = Ink,
    primaryContainer = RedTintDark,
    onPrimaryContainer = Color(0xFFFFB4AB),
    secondary = PaperMuted,
    onSecondary = Ink,
    secondaryContainer = CardDarkHigh,
    onSecondaryContainer = Paper,
    tertiary = AmberDark,
    onTertiary = Ink,
    tertiaryContainer = AmberTintDark,
    onTertiaryContainer = Color(0xFFFFD9A8),
    background = PageDark,
    onBackground = Paper,
    surface = PageDark,
    onSurface = Paper,
    surfaceVariant = PageDark,
    onSurfaceVariant = PaperMuted,
    outline = PaperMuted,
    outlineVariant = HairlineDark,
    surfaceContainerLowest = CardDark,
    surfaceContainerLow = CardDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = CardDarkHigh,
    surfaceContainerHighest = CardDarkHigh,
    inverseSurface = CardDarkHigh,
    inverseOnSurface = Paper,
    inversePrimary = RedDark,
    scrim = Color(0xFF000000),
)

/**
 * The one dark panel on each screen: the promise on the first, the count on the second.
 *
 * It stays dark in both modes, which is what makes it the first thing the eye lands on.
 * In dark mode it lifts a step off the page instead of sinking into it.
 */
@Immutable
data class Hero(val container: Color, val content: Color, val muted: Color, val accent: Color)

private val HeroLight = Hero(Ink, Paper, Color(0xFFB9BDC7), RedDark)
private val HeroDark = Hero(Color(0xFF1E2229), Paper, PaperMuted, RedDark)

val LocalHero = staticCompositionLocalOf { HeroLight }

// Corners large enough to read as a 2026 phone rather than a 2012 form, and never fully
// round: a pill on every button is the one shape that makes every app look like every
// other app.
private val RedlineShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// Four sizes do the work: the count, the headline, the title and the body. Display type
// is tightened, because large text at default tracking looks loose and unfinished.
private val base = Typography()
private val RedlineType = base.copy(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 64.sp, letterSpacing = (-1.5).sp),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    // Material's body tracking (0.25 to 0.5sp) assumes glanceable UI text and reads airy on
    // a paragraph of legal reasons. Body sits near zero, small text slightly positive, and
    // body and meta are a size up because what they carry is meant to be read, not scanned.
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.1.sp),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    bodySmall = base.bodySmall.copy(letterSpacing = 0.2.sp),
)

/** The quoted clause, set like the document it came from rather than like the app. */
val QuoteStyle = TextStyle(
    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
    fontSize = 15.sp,
    lineHeight = 22.sp,
)

/**
 * One set of springs, easings and stagger for the whole app, so the scan, the count, the
 * cards and the reveal move as if one hand made them.
 *
 * Material's own MotionScheme would supply these but is internal in material3 1.4.0, so the
 * values are copied from its token files. Springs start from wherever a value currently is,
 * which is what keeps an interrupted animation from jumping.
 */
internal object RedlineMotion {
    /** Layout moves: critically damped enough to settle without a wobble. */
    fun <T> spatial() = spring<T>(dampingRatio = 0.9f, stiffness = 700f)
    /** The scan hand-off, the one move allowed a little give. */
    fun <T> spatialExpressive() = spring<T>(dampingRatio = 0.8f, stiffness = 380f)
    /** A nudge on the call to action, nowhere else. */
    fun <T> spatialBouncy() = spring<T>(dampingRatio = 0.6f, stiffness = 800f)
    /** Alpha and colour. */
    fun <T> effects() = spring<T>(dampingRatio = 1f, stiffness = 1600f)
    /** Press feedback. */
    fun <T> effectsFast() = spring<T>(dampingRatio = 1f, stiffness = 3800f)
    /** Material emphasized decelerate, for things arriving. */
    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** Material emphasized accelerate, for things leaving, which always go faster. */
    val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    const val STAGGER_MS = 40
    const val STAGGER_CAP = 6
}

/**
 * Zero when the reader has turned animations off system-wide.
 *
 * Developer options and the accessibility "remove animations" setting both write this,
 * and honouring it costs one read. An app that keeps animating after someone has asked
 * the whole device to stop is not being expressive, it is ignoring an instruction.
 */
@Composable
internal fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f) > 0f
    }
}

/** [spec] when animations are on, and the final frame at once when the reader turned them off. */
@Composable
internal fun <T> motion(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (animationsEnabled()) spec else snap()

// Dynamic colour is off on purpose: the demo recording has to look the same on
// any machine that plays it.
@Composable
fun RedlineTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalHero provides if (dark) HeroDark else HeroLight) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            shapes = RedlineShapes,
            typography = RedlineType,
            content = content,
        )
    }
}
