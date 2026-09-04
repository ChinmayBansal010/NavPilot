package com.navpilot.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val NavPilotColors = lightColorScheme(
    primary = Brand,
    onPrimary = Surface,
    background = Bg,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    outline = Line,
    error = Danger
)

private val NavPilotTypography = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontSize = 15.sp),
        bodyMedium = bodyMedium.copy(fontSize = 13.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
    )
}

@Composable
fun NavPilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NavPilotColors,
        typography = NavPilotTypography,
        content = content
    )
}
