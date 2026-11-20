package cybergarden.vinity.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import cybergarden.vinity.app.resources.Res
import cybergarden.vinity.app.resources.droid_sans
import cybergarden.vinity.app.resources.droid_sans_bold
import org.jetbrains.compose.resources.Font

@Composable
fun VinityTheme(content: @Composable () -> Unit) {
    val family = FontFamily(
        Font(Res.font.droid_sans, FontWeight.Normal),
        Font(Res.font.droid_sans, FontWeight.Medium),
        Font(Res.font.droid_sans_bold, FontWeight.SemiBold),
        Font(Res.font.droid_sans_bold, FontWeight.Bold),
    )
    MaterialTheme(typography = Typography().withFamily(family), content = content)
}

private fun Typography.withFamily(family: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)
