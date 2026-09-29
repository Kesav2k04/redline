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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kesav.redline.R
import dev.kesav.redline.Severity

/**
 * Three faces, each with one job.
 *
 * Geist carries the interface. Geist Mono carries every figure the app computes (the score,
 * a sum of money, a clause count), so numbers line up and read as measured rather than as
 * prose. Source Serif carries the lease itself, wherever it is quoted, because a clause set in
 * the app's own face looks like the app's opinion and a clause set like print looks like the
 * document the reader signed. All three are variable fonts, one file each.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
internal object RedlineFonts {
    private fun variable(res: Int, weight: FontWeight) = Font(
        res,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

    private val weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)

    val Sans = FontFamily(weights.map { variable(R.font.geist, it) })
    val Mono = FontFamily(weights.map { variable(R.font.geist_mono, it) })
    val Serif = FontFamily(listOf(FontWeight.Normal, FontWeight.SemiBold).map { variable(R.font.source_serif, it) })
}

// A cool neutral page with white cards a step above it, one hairline for every edge, and
// colour kept for meaning: red is a clause that costs money, amber is one worth checking,
// green is a clause that passed. Nothing decorative is coloured, so colour always says
// something.
private val Page = Color(0xFFF3F4F6)
private val Card = Color(0xFFFFFFFF)
private val Ink = Color(0xFF0A0B0D)
private val InkMuted = Color(0xFF5B606B)    // 6.2:1 on Page
private val Hairline = Color(0xFFE2E4E9)
private val Red = Color(0xFFD92D20)          // 4.8:1 on Card
private val RedTint = Color(0xFFFEECEA)
private val Amber = Color(0xFFA15C00)        // 5.0:1 on Card
private val AmberTint = Color(0xFFFFF3DF)
private val Green = Color(0xFF12805C)        // 5.0:1 on Card
private val GreenTint = Color(0xFFE3F6EE)

private val PageDark = Color(0xFF09090B)
private val CardDark = Color(0xFF141519)
private val CardDarkHigh = Color(0xFF1D1F25)
private val Paper = Color(0xFFF4F5F7)
private val PaperMuted = Color(0xFFA2A7B3)
private val HairlineDark = Color(0xFF2A2D35)
private val RedDark = Color(0xFFFF6A5C)
private val RedTintDark = Color(0xFF3B1714)
private val AmberDark = Color(0xFFF5A83B)
private val AmberTintDark = Color(0xFF372511)
private val GreenDark = Color(0xFF4CD4A0)
private val GreenTintDark = Color(0xFF0F2E24)

// The report card's own palette. It is the same in both themes because the card is always dark.
internal val Obsidian = Color(0xFF111318)
internal val HeroPaper = Color(0xFFF2F3F5)
internal val Crimson = Color(0xFFFF4D4D)
internal val HeroAmber = AmberDark

// Every role is set. Any role left out falls back to Material's baseline palette, which
// is violet, and that is how the first bottom sheet this app showed came up lilac.
private val Light = lightColorScheme(
    primary = Red,
    onPrimary = Color.White,
    primaryContainer = RedTint,
    onPrimaryContainer = Color(0xFF7A140F),
    secondary = InkMuted,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EAEE),
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
    surfaceContainerHighest = Color(0xFFE8EAEE),
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    inversePrimary = RedDark,
    error = Red,
    onError = Color.White,
    errorContainer = RedTint,
    onErrorContainer = Color(0xFF7A140F),
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
    error = RedDark,
    onError = Ink,
    errorContainer = RedTintDark,
    onErrorContainer = Color(0xFFFFB4AB),
    scrim = Color(0xFF000000),
)

/**
 * The one dark panel on each screen: the promise on the first, the score on the second.
 *
 * It stays dark in both modes, which is what makes it the first thing the eye lands on.
 * In dark mode it lifts a step off the page instead of sinking into it.
 */
@Immutable
data class Hero(
    val container: Color,
    val content: Color,
    val muted: Color,
    val accent: Color,
    /** Set where the card would vanish into the page: Obsidian on the dark page is 1.07:1. */
    val border: Color? = null,
)

private val HeroLight = Hero(Obsidian, HeroPaper, PaperMuted, Crimson)
private val HeroDark = Hero(Obsidian, HeroPaper, PaperMuted, Crimson, border = HeroPaper.copy(alpha = 0.08f))

/**
 * The dial's band and tier label for a score: paper, amber, crimson. Lightness falls as risk
 * rises, so the order holds under any colour deficiency. Green is not on this ramp, because
 * green says clean and the report never claims that. Thresholds match [RiskColors.ofScore].
 */
internal fun dialColor(score: Int): Color = when {
    score < 30 -> HeroPaper
    score < 60 -> HeroAmber
    else -> Crimson
}

val LocalHero = staticCompositionLocalOf { HeroLight }

/**
 * What each level of risk looks like, as a strong colour for marks and figures and a tint
 * for the ground behind them. HIGH and MEDIUM are the primary and tertiary roles, so code that
 * only knows the colour scheme draws the same red and amber.
 */
@Immutable
data class RiskColors(
    val high: Color,
    val highTint: Color,
    val medium: Color,
    val mediumTint: Color,
    val low: Color,
    val lowTint: Color,
) {
    fun of(severity: Severity): Color = if (severity == Severity.HIGH) high else medium
    fun tintOf(severity: Severity): Color = if (severity == Severity.HIGH) highTint else mediumTint

    /** A 0 to 100 score as a colour: green below 30, amber below 60, red from there. */
    fun ofScore(score: Int): Color = when {
        score < 30 -> low
        score < 60 -> medium
        else -> high
    }

    fun tintOfScore(score: Int): Color = when {
        score < 30 -> lowTint
        score < 60 -> mediumTint
        else -> highTint
    }
}

private val RiskLight = RiskColors(Red, RedTint, Amber, AmberTint, Green, GreenTint)
private val RiskDark = RiskColors(RedDark, RedTintDark, AmberDark, AmberTintDark, GreenDark, GreenTintDark)

val LocalRisk = staticCompositionLocalOf { RiskLight }

/** The risk colours for the current theme. */
internal val risk: RiskColors
    @Composable @ReadOnlyComposable get() = LocalRisk.current

/**
 * Spacing on a 4dp grid. Space between groups is always larger than space inside them, which
 * is most of what makes a screen read as sections rather than as one list.
 */
internal object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    /** The side margin of every screen. */
    val gutter = 20.dp
}

// Corners large enough to read as a 2026 phone rather than a 2012 form, and never fully
// round: a pill on every button is the one shape that makes every app look like every
// other app.
private val RedlineShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// Display type is tight and heavy, body type is open, labels are a size up from Material's
// defaults because what they carry is meant to be read. Every style is Geist.
private val base = Typography()
private fun TextStyle.geist() = copy(fontFamily = RedlineFonts.Sans)
private val RedlineType = Typography(
    displayLarge = base.displayLarge.geist().copy(fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 64.sp, letterSpacing = (-2.4).sp),
    displayMedium = base.displayMedium.geist().copy(fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1.6).sp),
    displaySmall = base.displaySmall.geist().copy(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-1.1).sp),
    headlineLarge = base.headlineLarge.geist().copy(fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.8).sp),
    headlineMedium = base.headlineMedium.geist().copy(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.6).sp),
    headlineSmall = base.headlineSmall.geist().copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp),
    titleLarge = base.titleLarge.geist().copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp),
    titleMedium = base.titleMedium.geist().copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = (-0.1).sp),
    titleSmall = base.titleSmall.geist().copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    labelLarge = base.labelLarge.geist().copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.sp),
    labelMedium = base.labelMedium.geist().copy(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelSmall = base.labelSmall.geist().copy(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    bodyLarge = base.bodyLarge.geist().copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.geist().copy(fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodySmall = base.bodySmall.geist().copy(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
)

/** The quoted clause, set like the document it came from rather than like the app. */
val QuoteStyle = TextStyle(
    fontFamily = RedlineFonts.Serif,
    fontSize = 15.sp,
    lineHeight = 23.sp,
)

/** Every computed figure: the score, money, counts. Tabular, so digits never jitter. */
internal val FigureStyle = TextStyle(
    fontFamily = RedlineFonts.Mono,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
    letterSpacing = (-0.5).sp,
)

/** Small uppercase labels above a figure ("RISK SCORE", "STATED IN THE LEASE"). */
internal val EyebrowStyle = TextStyle(
    fontFamily = RedlineFonts.Mono,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.2.sp,
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
    /** The scan hand-off and the gauge, the moves allowed a little give. */
    fun <T> spatialExpressive() = spring<T>(dampingRatio = 0.8f, stiffness = 380f)
    /** A nudge on the call to action, nowhere else. */
    fun <T> spatialBouncy() = spring<T>(dampingRatio = 0.6f, stiffness = 800f)
    /** Cards opening and closing: quick, with a small settle at the end. */
    fun <T> expand() = spring<T>(dampingRatio = 0.82f, stiffness = 520f)
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
    CompositionLocalProvider(
        LocalHero provides if (dark) HeroDark else HeroLight,
        LocalRisk provides if (dark) RiskDark else RiskLight,
    ) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            shapes = RedlineShapes,
            typography = RedlineType,
            content = content,
        )
    }
}
