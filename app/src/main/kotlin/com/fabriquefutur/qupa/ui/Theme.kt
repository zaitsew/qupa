package com.fabriquefutur.qupa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.fabriquefutur.qupa.game.PlayerColors

/** Dark party palette. The four answer colours match the phone page. */
object QupaColors {
    val Background = Color(0xFF120F2B)
    val Card = Color(0xFF1F1A3F)
    val CardHigh = Color(0xFF2D2660)
    val Track = Color(0xFF3A3370)
    val Text = Color(0xFFF4F2FF)
    val Muted = Color(0xFFB7B0DB)
    val Accent = Color(0xFFA78BFA)
    val Focus = Color(0xFFFFFFFF)
    val Ok = Color(0xFF06D6A0)
    val Bad = Color(0xFFEF476F)
    val Ink = Color(0xFF10122B)

    /** A red, B blue, C yellow, D green; shapes triangle, diamond, circle, square. */
    val Answer = listOf(Color(0xFFEF476F), Color(0xFF4EA8DE), Color(0xFFFFD166), Color(0xFF06D6A0))

    fun player(index: Int): Color = hex(PlayerColors.hex(index))

    private fun hex(h: String): Color = Color(0xFF000000L or h.removePrefix("#").toLong(16))
}

/** TV safe area: 5% of a 960x540 dp screen on each side. */
val SafeHorizontal = 48.dp
val SafeVertical = 27.dp

@Composable
fun QupaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = QupaColors.Accent,
            onPrimary = QupaColors.Ink,
            background = QupaColors.Background,
            onBackground = QupaColors.Text,
            surface = QupaColors.Card,
            onSurface = QupaColors.Text,
            surfaceVariant = QupaColors.CardHigh,
            onSurfaceVariant = QupaColors.Muted,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(QupaColors.Background)) { content() }
    }
}
