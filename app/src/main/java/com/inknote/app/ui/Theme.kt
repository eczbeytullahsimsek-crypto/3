package com.inknote.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.LineHeightStyle
import com.inknote.app.R

/** Tasarımdaki Tailwind/Material 3 renk jetonları (birebir). */
object Fx {
    val primaryFixed = Color(0xFFE0E0FF)
    val primaryFixedDim = Color(0xFFBFC2FF)
    val onPrimaryFixed = Color(0xFF02006D)
    val onPrimaryFixedVariant = Color(0xFF3237A7)
    val secondaryFixed = Color(0xFFD5E6E0)
    val secondaryFixedDim = Color(0xFFB9CAC4)
    val tertiaryFixed = Color(0xFFFFD7F1)
    val tertiaryFixedDim = Color(0xFFF8B0E4)
    val onTertiaryFixed = Color(0xFF370531)
    val onSecondaryFixed = Color(0xFF0F1E1B)
    val amber200 = Color(0xFFFDE68A)
    val amber950 = Color(0xFF451A03)
    val emerald600 = Color(0xFF059669)
    val sticky = Color(0xFFFEF39E)
    val paper = Color(0xFFFCFAF4)
}

private val InkScheme = lightColorScheme(
    primary = Color(0xFF363BAB), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF4F55C4), onPrimaryContainer = Color(0xFFDBDBFF),
    secondary = Color(0xFF52625D), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E4DD), onSecondaryContainer = Color(0xFF566661),
    tertiary = Color(0xFF6E3763), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF894E7C), onTertiaryContainer = Color(0xFFFFD1F0),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFDF7FF), onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFDF7FF), onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFE6E1E9), onSurfaceVariant = Color(0xFF464653),
    outline = Color(0xFF767684), outlineVariant = Color(0xFFC6C5D5),
    inverseSurface = Color(0xFF312F36), inverseOnSurface = Color(0xFFF4EFF7), inversePrimary = Color(0xFFBFC2FF),
    surfaceTint = Color(0xFF4B51C0),
    surfaceBright = Color(0xFFFDF7FF), surfaceDim = Color(0xFFDDD8E0),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF2ECF4), surfaceContainerHigh = Color(0xFFECE6EF),
    surfaceContainerHighest = Color(0xFFE6E1E9),
)

// Tasarımdaki borderRadius: DEFAULT 1rem, lg 2rem, xl 3rem
private val InkShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(32.dp), extraLarge = RoundedCornerShape(48.dp),
)

@OptIn(ExperimentalTextApi::class)
val RobotoFlex = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(R.font.roboto_flex, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

/** Tasarımın font ölçeği (Roboto Flex). */
object TS {
    @OptIn(ExperimentalTextApi::class)
    private fun s(size: Int, lh: Int, ls: Float, w: Int) = TextStyle(
        fontFamily = RobotoFlex, fontSize = size.sp, lineHeight = lh.sp, letterSpacing = ls.sp,
        fontWeight = FontWeight(w), platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    val labelLg = s(14, 20, 0.1f, 500)
    val labelMd = s(12, 16, 0.5f, 500)
    val labelSm = s(11, 16, 0.5f, 500)
    val bodySm = s(12, 16, 0.4f, 400)
    val bodyMd = s(14, 20, 0.25f, 400)
    val bodyLg = s(16, 24, 0.5f, 400)
    val titleSm = s(14, 20, 0.1f, 500)
    val titleMd = s(16, 24, 0.15f, 600)
    val titleLg = s(22, 28, 0f, 500)
    val headlineLg = s(32, 40, 0f, 400)
}

@Composable
fun InkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = InkScheme, shapes = InkShapes) {
        androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides InkScheme.onSurface) {
            content()
        }
    }
}

// ------------------------------------------------------------------ Material Symbols Outlined (ligature font)

@OptIn(ExperimentalTextApi::class)
private val SymbolsOutline = FontFamily(
    Font(R.font.material_symbols, variationSettings = FontVariation.Settings(
        FontVariation.weight(400), FontVariation.Setting("FILL", 0f), FontVariation.Setting("GRAD", 0f), FontVariation.Setting("opsz", 24f)))
)

@OptIn(ExperimentalTextApi::class)
private val SymbolsFilled = FontFamily(
    Font(R.font.material_symbols, variationSettings = FontVariation.Settings(
        FontVariation.weight(400), FontVariation.Setting("FILL", 1f), FontVariation.Setting("GRAD", 0f), FontVariation.Setting("opsz", 24f)))
)

/** Tasarımdaki `material-symbols-outlined` span'inin birebir karşılığı. */
@Composable
fun Sym(
    name: String,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    fill: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val sp = with(LocalDensity.current) { size.toSp() }
    Text(
        text = name,
        modifier = modifier,
        color = tint,
        maxLines = 1,
        softWrap = false,
        style = TextStyle(
            fontFamily = if (fill) SymbolsFilled else SymbolsOutline,
            fontSize = sp, lineHeight = sp, letterSpacing = 0.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    )
}
