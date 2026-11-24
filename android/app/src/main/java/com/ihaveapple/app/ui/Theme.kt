package com.ihaveapple.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihaveapple.app.R

// 设计 Token（docs/windows-ui-design.md）
val Curtain = Color(0xFF141016)
val Wing = Color(0xFF1F1922)
val Proscenium = Color(0xFF2A2230)
val Line = Color(0xFF3A3040)
val Subtitle = Color(0xFFF3EDE6)
val Aside = Color(0xFF9A8F9C)
val Crabapple = Color(0xFFE8436A)
val CrabappleDeep = Color(0xFFC42A52)
val Stage = Color(0xFF000000)
val Danger = Color(0xFFFF7A6B)

val Subtitle08 = Subtitle.copy(alpha = 0.08f)
val Subtitle14 = Subtitle.copy(alpha = 0.14f)
val Subtitle25 = Subtitle.copy(alpha = 0.25f)
val Subtitle30 = Subtitle.copy(alpha = 0.30f)
val Subtitle70 = Subtitle.copy(alpha = 0.70f)
val Stage60 = Stage.copy(alpha = 0.60f)
val Scrim = Curtain.copy(alpha = 0.72f)
val ScrimDeep = Curtain.copy(alpha = 0.94f)

val SpaceXs = 4.dp
val SpaceSm = 8.dp
val SpaceMd = 12.dp
val SpaceLg = 16.dp
val SpaceXl = 24.dp
val SpaceXxl = 32.dp
val SpaceXxxl = 48.dp

val RadiusSm = 4.dp
val RadiusMd = 8.dp
val RadiusLg = 12.dp

val LayoutTitleBar = 40.dp
val LayoutChannelBar = 32.dp
val LayoutControlsBar = 56.dp
val LayoutEpisodeCell = 40.dp
val LayoutEpisodeTab = 28.dp
val LayoutDramaInfo = 92.dp
val LayoutDramaRow = 88.dp
val LayoutCoverThumbWidth = 54.dp
val LayoutCoverThumbHeight = 72.dp

val UiFont = FontFamily(
    Font(R.font.harmonyos_sans_regular, FontWeight.Normal),
    Font(R.font.harmonyos_sans_medium, FontWeight.Medium),
    Font(R.font.harmonyos_sans_bold, FontWeight.Bold),
)

val DisplayFont = FontFamily(Font(R.font.smiley_sans_oblique, FontWeight.Normal))

val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFont, fontSize = 56.sp, lineHeight = 56.sp),
    headlineSmall = TextStyle(fontFamily = UiFont, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = UiFont, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontFamily = UiFont, fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = UiFont, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = UiFont, fontSize = 12.sp, lineHeight = 18.sp),
)

val EpisodeNumberStyle = TextStyle(
    fontFamily = DisplayFont,
    fontSize = 18.sp,
    lineHeight = 24.sp,
)

val MonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    fontFeatureSettings = "tnum",
)

val PillShape = RoundedCornerShape(percent = 50)

val LocalControlsVisible = staticCompositionLocalOf { true }