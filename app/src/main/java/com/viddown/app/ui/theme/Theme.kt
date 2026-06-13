package com.viddown.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Colors ──────────────────────────────────────

val RedPrimary     = Color(0xFFE53935)
val RedDark        = Color(0xFFB71C1C)
val OrangeAccent   = Color(0xFFFF6D00)
val BgDark         = Color(0xFF0A0A0A)
val Surface1       = Color(0xFF141414)
val Surface2       = Color(0xFF1E1E1E)
val Surface3       = Color(0xFF282828)
val OnBg           = Color(0xFFFFFFFF)
val OnSurface1     = Color(0xFFE0E0E0)
val OnSurface2     = Color(0xFF9E9E9E)
val Divider        = Color(0xFF2C2C2C)
val GreenSuccess   = Color(0xFF43A047)
val RedError       = Color(0xFFE53935)
val YellowWarning  = Color(0xFFFFA000)

private val DarkColorScheme = darkColorScheme(
    primary          = RedPrimary,
    onPrimary        = Color.White,
    primaryContainer = RedDark,
    secondary        = OrangeAccent,
    background       = BgDark,
    surface          = Surface1,
    surfaceVariant   = Surface2,
    onBackground     = OnBg,
    onSurface        = OnSurface1,
    onSurfaceVariant = OnSurface2,
    outline          = Divider,
    error            = RedError
)

// ── Typography ───────────────────────────────────

val VidDownTypography = Typography(
    headlineLarge  = TextStyle(fontWeight = FontWeight.Bold,   fontSize = 28.sp, color = OnBg),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold,   fontSize = 22.sp, color = OnBg),
    headlineSmall  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = OnBg),
    titleLarge     = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = OnBg),
    titleMedium    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = OnBg),
    bodyLarge      = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, color = OnSurface1),
    bodyMedium     = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, color = OnSurface1),
    bodySmall      = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, color = OnSurface2),
    labelMedium    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, color = OnSurface2),
)

// ── Theme ────────────────────────────────────────

@Composable
fun VidDownTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = VidDownTypography,
        content     = content
    )
}
