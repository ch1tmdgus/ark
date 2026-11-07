package app.n4c.ark

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0F766E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF042F2E),
    tertiary = Color(0xFFB45309),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),
    background = Color(0xFFFBFAFF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBFAFF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE4E3F1),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLow = Color(0xFFF3F2FB),
    surfaceContainer = Color(0xFFEEEDF7),
    surfaceContainerHigh = Color(0xFFE8E7F2),
    surfaceContainerHighest = Color(0xFFE2E1EC),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C5D0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF115E59),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFFFCD34D),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFF0F0F17),
    onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF0F0F17),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceContainerLow = Color(0xFF17171F),
    surfaceContainer = Color(0xFF1B1B24),
    surfaceContainerHigh = Color(0xFF25252F),
    surfaceContainerHighest = Color(0xFF30303A),
    outline = Color(0xFF918F9A),
    outlineVariant = Color(0xFF46464F)
)

@Composable
fun ArkTheme(dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
