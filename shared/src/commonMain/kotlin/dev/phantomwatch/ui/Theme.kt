package dev.phantomwatch.ui

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

val Rubik: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.rubik_regular, FontWeight.Normal),
        Font(Res.font.rubik_medium, FontWeight.Medium),
        Font(Res.font.rubik_semibold, FontWeight.SemiBold),
        Font(Res.font.rubik_bold, FontWeight.Bold),
    )

val Inter: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
    )

val Mono: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.ibmplexmono_regular, FontWeight.Normal),
        Font(Res.font.ibmplexmono_medium, FontWeight.Medium),
        Font(Res.font.ibmplexmono_semibold, FontWeight.SemiBold),
        Font(Res.font.ibmplexmono_bold, FontWeight.Bold),
    )

// single weight display face, used only on the chart cards
val Lilita: FontFamily
    @Composable get() = FontFamily(Font(Res.font.lilita_one_regular, FontWeight.Normal))
