package com.practicepartner.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

val Ink = Color(0xFF17324D)
val Muted = Color(0xFF5D7890)
val Cream = Color(0xFFEDF9FF)
val Paper = Color(0xFFFFFFFF)
val Forest = Color(0xFF287BC1)
val Sage = Color(0xFFDDF3FF)
val Amber = Color(0xFFFFC857)
val Danger = Color(0xFFC94F5C)

val SkyTop = Color(0xFFBDEBFF)
val SkyBottom = Color(0xFFF1FAFF)
val SkyBlue = Color(0xFF72C9F4)
val DeepSky = Color(0xFF287BC1)
val CloudBlue = Color(0xFFDDF3FF)
val CloudWhite = Color(0xFFFFFEFF)
val SunYellow = Color(0xFFFFD166)
val MintCloud = Color(0xFFDFF7EE)
val LavenderCloud = Color(0xFFEDE8FF)
val SkyOutline = Color(0xFFA8DDF5)

private val practiceColors = lightColorScheme(
    primary = DeepSky,
    onPrimary = Color.White,
    primaryContainer = CloudBlue,
    onPrimaryContainer = Ink,
    secondary = SunYellow,
    onSecondary = Ink,
    secondaryContainer = Color(0xFFFFF1C7),
    onSecondaryContainer = Ink,
    tertiary = Color(0xFF7469D8),
    tertiaryContainer = LavenderCloud,
    onTertiaryContainer = Ink,
    background = Cream,
    onBackground = Ink,
    surface = CloudWhite,
    onSurface = Ink,
    surfaceVariant = CloudBlue,
    onSurfaceVariant = Ink,
    outline = SkyOutline,
    error = Danger,
)

private val defaultTypography = Typography()
private val practiceTypography = Typography(
    displaySmall = defaultTypography.displaySmall.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
    ),
    headlineMedium = defaultTypography.headlineMedium.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
    ),
    headlineSmall = defaultTypography.headlineSmall.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
    ),
    titleLarge = defaultTypography.titleLarge.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
    ),
    titleMedium = defaultTypography.titleMedium.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
    ),
    labelLarge = defaultTypography.labelLarge.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
    ),
)

private val practiceShapes = Shapes(
    small = RoundedCornerShape(14),
    medium = RoundedCornerShape(22),
    large = RoundedCornerShape(30),
)

@Composable
fun PracticePartnerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = practiceColors,
        typography = practiceTypography,
        shapes = practiceShapes,
        content = content,
    )
}

@Composable
fun SkyAppBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SkyTop, SkyBottom),
                ),
            ),
    ) {
        content()
    }
}
