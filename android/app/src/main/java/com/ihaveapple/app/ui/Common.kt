package com.ihaveapple.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

@Composable
fun IhaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Crabapple,
            onPrimary = Subtitle,
            secondary = CrabappleDeep,
            background = Curtain,
            onBackground = Subtitle,
            surface = Wing,
            onSurface = Subtitle,
            surfaceVariant = Proscenium,
            onSurfaceVariant = Aside,
            outline = Line,
        ),
        typography = AppTypography,
        content = content,
    )
}

@Composable
fun IconBtn(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    tint: Color = if (active) Crabapple else Subtitle,
    size: Dp = 40.dp,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = modifier.size(size)) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun TextBtn(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Crabapple else Subtitle,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun ScrimTop(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to Stage60,
                1f to Color.Transparent,
            )
        )
    )
}

@Composable
fun ScrimBottom(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to Color.Transparent,
                1f to ScrimDeep,
            )
        )
    )
}

@Composable
fun Cover(
    url: String,
    modifier: Modifier = Modifier,
    corner: Dp = RadiusMd,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Crop,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Wing),
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = Aside,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = SpaceLg, bottom = SpaceSm),
    )
}

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    note: String = "",
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Subtitle, fontSize = 14.sp)
            if (note.isNotBlank()) {
                Text(note, color = Aside, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(SpaceMd))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Subtitle,
                checkedTrackColor = Crabapple,
                uncheckedThumbColor = Aside,
                uncheckedTrackColor = Proscenium,
                uncheckedBorderColor = Line,
            ),
        )
    }
}

@Composable
fun ChoiceRow(
    title: String,
    options: List<Pair<String, String>>,
    value: String,
    note: String = "",
    onPick: (String) -> Unit,
) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(title, color = Subtitle, fontSize = 14.sp)
        if (note.isNotBlank()) Text(note, color = Aside, fontSize = 12.sp)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = SpaceSm),
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            for ((v, label) in options) {
                val active = v == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(PillShape)
                        .background(if (active) Subtitle14 else Color.Transparent)
                        .border(1.dp, if (active) Crabapple else Line, PillShape)
                        .clickable { onPick(v) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (active) Crabapple else Aside, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Line.copy(alpha = 0.5f))
    )
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(text, color = Aside, fontSize = 13.sp)
    }
}

@Composable
fun ElidedTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Subtitle,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun rememberScrimBrush(): Brush = remember {
    Brush.verticalGradient(0f to Color.Transparent, 1f to ScrimDeep)
}