package ai.daakia.callkit.sample.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFF7C4DFF)
private val BrandDark = Color(0xFFB39DFF)

private val LightColors =
    lightColorScheme(
        primary = Brand,
        secondary = Color(0xFF0A2A43),
    )

private val DarkColors =
    darkColorScheme(
        primary = BrandDark,
        secondary = Color(0xFF80CBC4),
    )

/** Material 3 theme for the sample; follows the system light/dark setting. */
@Composable
fun DaakiaSampleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
