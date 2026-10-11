package com.example.freevpn.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF090D16); val NavyCard = Color(0xFF111827); val ConnectGreen = Color(0xFF10B981)
val Danger = Color(0xFFF43F5E); val Amber = Color(0xFFF59E0B); val Cyan = Color(0xFF06B6D4)

private val Dark = darkColorScheme(
    primary = ConnectGreen, onPrimary = Color.Black, secondary = Cyan, background = Navy, surface = NavyCard,
    onBackground = Color(0xFFF8FAFC), onSurface = Color(0xFFF8FAFC), surfaceVariant = Color(0xFF1E293B), onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155))
private val Light = lightColorScheme(
    primary = Color(0xFF059669), onPrimary = Color.White, secondary = Color(0xFF0891B2), background = Color(0xFFF8FAFC), surface = Color.White,
    onBackground = Color(0xFF0F172A), onSurface = Color(0xFF0F172A), surfaceVariant = Color(0xFFF1F5F9), onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFE2E8F0))

@Composable
fun FreeVpnTheme(mode: com.example.freevpn.data.ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        com.example.freevpn.data.ThemeMode.System -> isSystemInDarkTheme()
        com.example.freevpn.data.ThemeMode.Dark -> true
        else -> false
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
