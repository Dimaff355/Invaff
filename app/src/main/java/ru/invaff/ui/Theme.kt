package ru.invaff.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ru.invaff.data.Kind

val Up = Color(0xFF3DD68C)
val Down = Color(0xFFF0616D)

/** Категориальная палитра долей (проверена на различимость при дальтонизме на тёмном фоне). */
val Kind.color
    get() = Color(
        when (this) {
            Kind.Share -> 0xFF3987E5
            Kind.Bond -> 0xFFD95926
            Kind.Fund -> 0xFF9085E9
            Kind.Currency -> 0xFFC98500
            Kind.Other -> 0xFFD55181
        }
    )

private val scheme = darkColorScheme(
    primary = Up,
    onPrimary = Color(0xFF00210F),
    background = Color(0xFF0E0F11),
    surface = Color(0xFF0E0F11),
    surfaceContainer = Color(0xFF16181C),
    surfaceContainerHigh = Color(0xFF1E2126),
    surfaceContainerHighest = Color(0xFF262A30),
    onSurface = Color(0xFFF2F3F5),
    onSurfaceVariant = Color(0xFF9AA0A8),
    outline = Color(0xFF3A3F47),
    outlineVariant = Color(0xFF2A2E35),
    secondaryContainer = Color(0xFF1F3A2C),
    onSecondaryContainer = Up,
    error = Down,
)

@Composable
fun InvaffTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)
