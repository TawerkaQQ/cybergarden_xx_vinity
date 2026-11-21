package cybergarden.vinity.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import cybergarden.vinity.app.resources.Res
import cybergarden.vinity.app.resources.inter_regular
import cybergarden.vinity.app.resources.inter_semibold
import org.jetbrains.compose.resources.Font

class Palette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val fill: Color,
    val fg: Color,
    val fg2: Color,
    val fg3: Color,
    val divider: Color,
    val border: Color,
    val primary: Color,
    val primaryHover: Color,
    val primaryInk: Color,
    val primary1: Color,
    val primary3: Color,
    val ok: Color,
    val okInk: Color,
    val okBg: Color,
    val okBd: Color,
    val err: Color,
    val errInk: Color,
    val errBg: Color,
    val errBd: Color,
    val warn: Color,
    val warnInk: Color,
    val warnBg: Color,
    val warnBd: Color,
    val c1: Color,
    val c2: Color,
    val c3: Color,
    val paper: Color,
    val hl: Color,
    val hlBorder: Color,
    val hlErr: Color,
    val hlErrBorder: Color,
)

private val Light = Palette(
    dark = false,
    bg = Color(0xFFF0F2F5), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFFAFAFA), fill = Color(0x0F000000),
    fg = Color(0xFF262626), fg2 = Color(0xFF8C8C8C), fg3 = Color(0xFFBFBFBF), divider = Color(0xFFF0F0F0), border = Color(0xFFD9D9D9),
    primary = Color(0xFF1890FF), primaryHover = Color(0xFF40A9FF), primaryInk = Color(0xFF096DD9),
    primary1 = Color(0xFFE6F7FF), primary3 = Color(0xFF91D5FF),
    ok = Color(0xFF52C41A), okInk = Color(0xFF389E0D), okBg = Color(0xFFF6FFED), okBd = Color(0xFFB7EB8F),
    err = Color(0xFFF5222D), errInk = Color(0xFFCF1322), errBg = Color(0xFFFFF1F0), errBd = Color(0xFFFFCCC7),
    warn = Color(0xFFFA8C16), warnInk = Color(0xFFD46B08), warnBg = Color(0xFFFFF7E6), warnBd = Color(0xFFFFD591),
    c1 = Color(0xFF1890FF), c2 = Color(0xFFFA8C16), c3 = Color(0xFF722ED1),
    paper = Color(0xFFFFFFFF),
    hl = Color(0x291890FF), hlBorder = Color(0xFF1890FF), hlErr = Color(0x1FF5222D), hlErrBorder = Color(0xFFF5222D),
)

private val Dark = Palette(
    dark = true,
    bg = Color(0xFF141414), surface = Color(0xFF1F1F1F), surface2 = Color(0xFF262626), fill = Color(0x14FFFFFF),
    fg = Color(0xFFE6E6E6), fg2 = Color(0xFF8C8C8C), fg3 = Color(0xFF595959), divider = Color(0xFF303030), border = Color(0xFF434343),
    primary = Color(0xFF177DDC), primaryHover = Color(0xFF3C9AE8), primaryInk = Color(0xFF3C9AE8),
    primary1 = Color(0xFF111D2C), primary3 = Color(0xFF15395B),
    ok = Color(0xFF49AA19), okInk = Color(0xFF6ABE39), okBg = Color(0xFF162312), okBd = Color(0xFF274916),
    err = Color(0xFFD32029), errInk = Color(0xFFE84749), errBg = Color(0xFF2A1215), errBd = Color(0xFF58181C),
    warn = Color(0xFFD87A16), warnInk = Color(0xFFE89A3C), warnBg = Color(0xFF2B1D11), warnBd = Color(0xFF593815),
    c1 = Color(0xFF3C9AE8), c2 = Color(0xFFD87A16), c3 = Color(0xFF854ECA),
    paper = Color(0xFFECECEC),
    hl = Color(0x381890FF), hlBorder = Color(0xFF1890FF), hlErr = Color(0x2EF5222D), hlErrBorder = Color(0xFFF5222D),
)

private val LocalPalette = staticCompositionLocalOf { Light }
private val LocalFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

val C: Palette
    @Composable @ReadOnlyComposable get() = LocalPalette.current

object T {
    val body: TextStyle @Composable @ReadOnlyComposable get() = style(14, 22)
    val bodyMedium: TextStyle @Composable @ReadOnlyComposable get() = style(14, 22, FontWeight.Medium)
    val small: TextStyle @Composable @ReadOnlyComposable get() = style(12, 16)
    val smallMedium: TextStyle @Composable @ReadOnlyComposable get() = style(12, 16, FontWeight.Medium)
    val mono: TextStyle @Composable @ReadOnlyComposable get() = style(12, 16).copy(fontFamily = FontFamily.Monospace)
    val h1: TextStyle @Composable @ReadOnlyComposable get() = style(20, 28, FontWeight.Medium)
    val h2: TextStyle @Composable @ReadOnlyComposable get() = style(16, 24, FontWeight.Medium)
    val h3: TextStyle @Composable @ReadOnlyComposable get() = style(14, 22, FontWeight.Medium)
    val alert: TextStyle @Composable @ReadOnlyComposable get() = style(16, 24)
    val tile: TextStyle @Composable @ReadOnlyComposable get() = style(32, 40, FontWeight.Medium)
    val big: TextStyle @Composable @ReadOnlyComposable get() = style(24, 32, FontWeight.Medium)
    val axis: TextStyle @Composable @ReadOnlyComposable get() = style(11, 14)

    @Composable
    @ReadOnlyComposable
    private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = LocalFamily.current,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        color = LocalPalette.current.fg,
    )
}

@Composable
fun VinityTheme(content: @Composable () -> Unit) {
    val palette = if (isSystemInDarkTheme()) Dark else Light
    val family = FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_semibold, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
        Font(Res.font.inter_semibold, FontWeight.Bold),
    )
    val scheme = if (palette.dark) {
        darkColorScheme(primary = palette.primary, background = palette.bg, surface = palette.surface, onSurface = palette.fg, onBackground = palette.fg)
    } else {
        lightColorScheme(primary = palette.primary, background = palette.bg, surface = palette.surface, onSurface = palette.fg, onBackground = palette.fg)
    }
    CompositionLocalProvider(LocalPalette provides palette, LocalFamily provides family) {
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                LocalTextStyle provides T.body,
                content = content,
            )
        }
    }
}
