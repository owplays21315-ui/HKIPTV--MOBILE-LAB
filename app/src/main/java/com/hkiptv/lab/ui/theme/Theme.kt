package com.hkiptv.lab.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AccentRed = Color(0xFFE11D2E)
private val colors = darkColorScheme(primary = AccentRed, secondary = Color(0xFFFF5964), background = Color(0xFF090909), surface = Color(0xFF121212), surfaceVariant = Color(0xFF222222), onPrimary = Color.White, onBackground = Color(0xFFF5F5F5), onSurface = Color(0xFFF5F5F5))

@Composable fun HkIptvTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
