package com.kissa.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class KissaPalette(
    val id: String,
    val name: String,
    val bg: Color,
    val bg2: Color,
    val surface: Color,
    val glow1: Color,
    val glow2: Color,
    val text: Color = Color(0xFFF4F1FA),
    val textDim: Color = Color(0xFFB4ADC7),
)

object Themes {
    val all = listOf(
        KissaPalette("midnight", "Midnight", Color(0xFF0B0A14), Color(0xFF151127), Color(0xFF1C1733), Color(0xFF5B3FD1), Color(0xFFFF5C8A)),
        KissaPalette("noir", "Noir", Color(0xFF070708), Color(0xFF111114), Color(0xFF19191E), Color(0xFF3C3C48), Color(0xFF8A7B6B)),
        KissaPalette("ocean", "Deep Ocean", Color(0xFF06111A), Color(0xFF0B1C2A), Color(0xFF10273A), Color(0xFF1B6C8F), Color(0xFF2FD3C0)),
        KissaPalette("plum", "Velvet Plum", Color(0xFF130813), Color(0xFF221022), Color(0xFF2E1530), Color(0xFF8E2C6E), Color(0xFFFF9A5C)),
        KissaPalette("forest", "Night Forest", Color(0xFF07110C), Color(0xFF0E1E16), Color(0xFF14291F), Color(0xFF1F7A53), Color(0xFFC8E86B)),
    )
    val accents = listOf(0xFFFF7A90, 0xFFA58BFF, 0xFF5FE0FF, 0xFFFFB454, 0xFF6BF0B2, 0xFFFF9E7A, 0xFFE8D4A8)
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}

val LocalPalette = staticCompositionLocalOf { Themes.all.first() }
val LocalAccent = staticCompositionLocalOf { Color(0xFFFF7A90) }
val LocalBackdrop = staticCompositionLocalOf { "aurora" }

val DisplayFont = FontFamily.Serif

@Composable
fun KissaTheme(themeId: String, accent: Long, backdrop: String, content: @Composable () -> Unit) {
    val target = Themes.byId(themeId)
    // animate colours so switching themes is a smooth cross-fade, not a flash
    val bg by animateColorAsState(target.bg, tween(500), label = "bg")
    val bg2 by animateColorAsState(target.bg2, tween(500), label = "bg2")
    val surface by animateColorAsState(target.surface, tween(500), label = "s")
    val g1 by animateColorAsState(target.glow1, tween(500), label = "g1")
    val g2 by animateColorAsState(target.glow2, tween(500), label = "g2")
    val acc by animateColorAsState(Color(accent), tween(400), label = "acc")
    val palette = target.copy(bg = bg, bg2 = bg2, surface = surface, glow1 = g1, glow2 = g2)
    val scheme = darkColorScheme(
        primary = acc, onPrimary = Color(0xFF14101F), secondary = g2, background = bg, surface = surface,
        onBackground = palette.text, onSurface = palette.text, surfaceVariant = bg2, onSurfaceVariant = palette.textDim,
        outline = Color.White.copy(alpha = 0.14f),
    )
    val typo = Typography(
        displaySmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp),
        headlineMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
        headlineSmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 29.sp),
        titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
        titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
        labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.2.sp),
        labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.3.sp),
        labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp),
    )
    CompositionLocalProvider(LocalPalette provides palette, LocalAccent provides acc, LocalBackdrop provides backdrop) {
        MaterialTheme(colorScheme = scheme, typography = typo, content = content)
    }
}
