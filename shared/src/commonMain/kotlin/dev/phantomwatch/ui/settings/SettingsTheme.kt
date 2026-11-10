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
import dev.phantomwatch.ui.Bg
import dev.phantomwatch.ui.Cyan
import dev.phantomwatch.ui.Rubik
import dev.phantomwatch.ui.Slate100
import dev.phantomwatch.ui.Slate400
import dev.phantomwatch.ui.Slate500
import dev.phantomwatch.ui.SurfaceColor

val SettingsCanvas = Bg
val SettingsSurface = SurfaceColor
val SettingsText = Slate100
val SettingsText2 = Slate400
val SettingsText3 = Slate500
val SettingsAccent = Cyan
val SettingsError = Color(0xFFF87171)
val SettingsHairline = Color(0x14FFFFFF)

private val SettingsScheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF04272E),
    secondary = Slate400,
    onSecondary = Bg,
    background = Bg,
    onBackground = Slate100,
    surface = SurfaceColor,
    onSurface = Slate100,
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Slate400,
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF2A2A2A),
    error = SettingsError,
    onError = Color(0xFF2A0606),
)

private val SettingsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
internal fun SettingsTheme(content: @Composable () -> Unit) {
    val base = MaterialTheme.typography
    val rubik = Rubik
    val typography = remember(rubik, base) {
        base.copy(
            headlineLarge = base.headlineLarge.copy(
                fontFamily = rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                letterSpacing = (-0.5).sp,
            ),
            headlineSmall = base.headlineSmall.copy(
                fontFamily = rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = (-0.2).sp,
            ),
            titleLarge = base.titleLarge.copy(
                fontFamily = rubik,
                fontWeight = FontWeight.Medium,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                letterSpacing = 0.sp,
            ),
            titleMedium = base.titleMedium.copy(
                fontFamily = rubik,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                letterSpacing = 0.sp,
            ),
            bodyLarge = base.bodyLarge.copy(
                fontFamily = rubik,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
            bodyMedium = base.bodyMedium.copy(
                fontFamily = rubik,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            labelMedium = base.labelMedium.copy(
                fontFamily = rubik,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.sp,
            ),
            labelSmall = base.labelSmall.copy(
                fontFamily = rubik,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        )
    }
    MaterialTheme(colorScheme = SettingsScheme, typography = typography, shapes = SettingsShapes, content = content)
}
