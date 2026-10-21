package com.pasaree.app.tema

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Token desain dari pasaree-design `tokens/warna.json` dan `tokens/tipografi.json`. */
object WarnaPasaree {
    val HijauDaun = Color(0xFF2F7D4F)
    val Kunyit = Color(0xFFE0A100)
    val Terakota = Color(0xFFC0563B)
    val Latar = Color(0xFFFFFDF7)
    val Teks = Color(0xFF1E1E1E)
}

private val Skema = lightColorScheme(
    primary = WarnaPasaree.HijauDaun,
    onPrimary = Color.White,
    secondary = WarnaPasaree.Kunyit,
    onSecondary = WarnaPasaree.Teks,
    tertiary = WarnaPasaree.Terakota,
    error = WarnaPasaree.Terakota,
    background = WarnaPasaree.Latar,
    onBackground = WarnaPasaree.Teks,
    surface = WarnaPasaree.Latar,
    onSurface = WarnaPasaree.Teks,
)

private val Tipografi = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
)

@Composable
fun TemaPasaree(isi: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Skema, typography = Tipografi, content = isi)
}
