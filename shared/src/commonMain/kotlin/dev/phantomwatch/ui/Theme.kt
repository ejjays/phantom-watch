package dev.phantomwatch.ui

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.phantomwatch.res.*
import org.jetbrains.compose.resources.Font

val Bg = Color(0xFF121011)
val SurfaceColor = Color(0xFF1E1E1E)
val Cyan = Color(0xFF22D3EE)
val WatchRed = Color(0xFFEB2F3D)
val StarYellow = Color(0xFFFACC15)
val Slate100 = Color(0xFFF1F5F9)
val Slate200 = Color(0xFFE2E8F0)
val Slate300 = Color(0xFFCBD5E1)
val Slate400 = Color(0xFF94A3B8)
val Slate500 = Color(0xFF64748B)
val Slate600 = Color(0xFF475569)

val WorkSans: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.work_sans_regular, FontWeight.Normal),
        Font(Res.font.work_sans_medium, FontWeight.Medium),
        Font(Res.font.work_sans_semibold, FontWeight.SemiBold),
        Font(Res.font.work_sans_bold, FontWeight.Bold),
    )

val Lilita: FontFamily
    @Composable get() = FontFamily(Font(Res.font.lilita_one_regular, FontWeight.Normal))

@Composable
fun PhantomTypography(): Typography {
    val f = WorkSans
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = f),
        displayMedium = base.displayMedium.copy(fontFamily = f),
        displaySmall = base.displaySmall.copy(fontFamily = f),
        headlineLarge = base.headlineLarge.copy(fontFamily = f),
        headlineMedium = base.headlineMedium.copy(fontFamily = f),
        headlineSmall = base.headlineSmall.copy(fontFamily = f),
        titleLarge = base.titleLarge.copy(fontFamily = f),
        titleMedium = base.titleMedium.copy(fontFamily = f),
        titleSmall = base.titleSmall.copy(fontFamily = f),
        bodyLarge = base.bodyLarge.copy(fontFamily = f),
        bodyMedium = base.bodyMedium.copy(fontFamily = f),
        bodySmall = base.bodySmall.copy(fontFamily = f),
        labelLarge = base.labelLarge.copy(fontFamily = f),
        labelMedium = base.labelMedium.copy(fontFamily = f),
        labelSmall = base.labelSmall.copy(fontFamily = f),
    )
}
