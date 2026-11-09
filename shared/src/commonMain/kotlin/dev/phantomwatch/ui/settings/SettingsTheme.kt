package dev.phantomwatch.ui.settings

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phantomwatch.ui.Inter

val SettingsCanvas = Color(0xFF08090B)
val SettingsSurface = Color(0xFF101216)
val SettingsRaised = Color(0xFF161A20)
val SettingsText = Color(0xFFF2F5F8)
val SettingsText2 = Color(0xFFA7B0BC)
val SettingsText3 = Color(0xFF7C8794)
val SettingsAccent = Color(0xFF22D3EE)
val SettingsAccentInk = Color(0xFF04272E)
val SettingsError = Color(0xFFF87171)
val SettingsHairline = Color(0x0FFFFFFF)

private val SettingsScheme = darkColorScheme(
    primary = SettingsAccent,
    onPrimary = SettingsAccentInk,
    primaryContainer = Color(0xFF0C3A43),
    onPrimaryContainer = Color(0xFFB6F4FF),
    secondary = SettingsText2,
    onSecondary = SettingsCanvas,
    secondaryContainer = SettingsRaised,
    onSecondaryContainer = SettingsText,
    background = SettingsCanvas,
    onBackground = SettingsText,
    surface = SettingsSurface,
    onSurface = SettingsText,
    surfaceVariant = SettingsRaised,
    onSurfaceVariant = SettingsText2,
    surfaceTint = SettingsAccent,
    inverseSurface = SettingsText,
    inverseOnSurface = SettingsCanvas,
    error = SettingsError,
    onError = Color(0xFF2A0606),
    errorContainer = Color(0xFF3B1113),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF3C444F),
    outlineVariant = Color(0xFF252B34),
    scrim = Color.Black,
    surfaceBright = Color(0xFF232830),
    surfaceDim = SettingsCanvas,
    surfaceContainerLowest = Color(0xFF0B0C0F),
    surfaceContainerLow = SettingsSurface,
    surfaceContainer = Color(0xFF13161B),
    surfaceContainerHigh = SettingsRaised,
    surfaceContainerHighest = Color(0xFF1C2128),
)

private val SettingsShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
internal fun SettingsTheme(content: @Composable () -> Unit) {
    val base = MaterialTheme.typography
    val inter = Inter
    val typography = remember(inter, base) {
        base.copy(
            headlineLarge = base.headlineLarge.copy(
                fontFamily = inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                letterSpacing = (-0.6).sp,
            ),
            headlineSmall = base.headlineSmall.copy(
                fontFamily = inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                letterSpacing = (-0.3).sp,
            ),
            titleLarge = base.titleLarge.copy(
                fontFamily = inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            ),
            titleMedium = base.titleMedium.copy(
                fontFamily = inter,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                letterSpacing = 0.sp,
            ),
            bodyLarge = base.bodyLarge.copy(
                fontFamily = inter,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
            bodyMedium = base.bodyMedium.copy(
                fontFamily = inter,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            labelMedium = base.labelMedium.copy(
                fontFamily = inter,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.sp,
            ),
            labelSmall = base.labelSmall.copy(
                fontFamily = inter,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        )
    }
    MaterialTheme(colorScheme = SettingsScheme, typography = typography, shapes = SettingsShapes, content = content)
}
