package com.ucbps.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Cyan = Color(0xFF22D3EE)
val Warn = Color(0xFFFBBF24)
val Danger = Color(0xFFF87171)
val Ok = Color(0xFF34D399)
val Bg = Color(0xFF0B1220)
val Card = Color(0xFF111A2E)
val MutedColor = Color(0xFF8AA0BD)

@Composable
fun UcbpsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan, onPrimary = Color(0xFF04202A), background = Bg, surface = Card,
            onBackground = Color(0xFFE6EDF7), onSurface = Color(0xFFE6EDF7),
        ),
        content = content,
    )
}
