package com.ihaveapple.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihaveapple.app.data.Prefs
import com.ihaveapple.app.player.PlayerViewModel
import com.ihaveapple.app.player.UiState

@Composable
fun SettingsOverlay(
    state: UiState,
    prefs: Prefs,
    vm: PlayerViewModel,
) {
    var confirmReset by remember { mutableStateOf(false) }

    AnimatedVisibility(visible = state.settingsOpen, enter = fadeIn(), exit = fadeOut()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(ScrimDeep),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpaceSm, vertical = SpaceXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "返回", onClick = vm::closeSettings)
                Spacer(Modifier.width(SpaceXs))
                Text("设置", color = Subtitle, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SpaceXl),
            ) {
                SectionTitle("播放")
                ToggleRow(
                    "切到后台时暂停播放",
                    checked = prefs.pauseOnBackground,
                    note = "回到前台后自动继续",
                    onCheckedChange = vm::setPauseOnBackground,
                )
                RowDivider()
                ToggleRow(
                    "自动播放下一集",
                    checked = prefs.autoNext,
                    note = "关闭后，每集播完停在最后一帧",
                    onCheckedChange = vm::setAutoNext,
                )
                RowDivider()
                ToggleRow(
                    "切集时显示集号",
                    checked = prefs.titleCard,
                    note = "画面左上角短暂显示「第 N 集」",
                    onCheckedChange = vm::setTitleCard,
                )
                RowDivider()
                ToggleRow(
                    "显示弹幕",
                    checked = prefs.danmaku,
                    note = "这一集的精选弹幕从画面上部横穿",
                    onCheckedChange = vm::setDanmaku,
                )
                RowDivider()
                ToggleRow(
                    "播放时保持屏幕常亮",
                    checked = prefs.keepAwake,
                    onCheckedChange = vm::setKeepAwake,
                )
                RowDivider()
                ChoiceRow(
                    "画质",
                    options = listOf("自动" to "自动", "1080P" to "1080P", "720P" to "720P", "480P" to "480P"),
                    value = prefs.quality,
                    note = "播放时也可以在控件栏切换",
                    onPick = vm::setQuality,
                )
                RowDivider()
                ChoiceRow(
                    "控件空闲后隐藏",
                    options = listOf("1500" to "1.5 秒", "2500" to "2.5 秒", "4000" to "4 秒"),
                    value = prefs.controlsIdleMs.toString(),
                    onPick = { vm.setControlsIdle(it.toIntOrNull() ?: 2500) },
                )

                SectionTitle("存储")
                Text(
                    "观看进度、收藏和搜索记录只保存在这台设备上。信息流会缓存最近一次的剧单，断网时也能先打开。",
                    color = Aside,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )

                SectionTitle("关于")
                Text("IHaveApple for Android · 0.1.0", color = Subtitle, fontSize = 13.sp)
                Text(
                    "原生 Android 复刻版，参照 IHaveApple 桌面版设计。内容来自红果短剧匿名接口，仅供个人学习使用。",
                    color = Aside,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )

                Spacer(Modifier.height(SpaceXl))
                Text(
                    "完全重置",
                    color = Danger,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(RadiusMd))
                        .clickable { confirmReset = true }
                        .padding(vertical = SpaceMd),
                )
                Spacer(Modifier.height(SpaceXxxl))
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Proscenium,
            titleContentColor = Subtitle,
            textContentColor = Aside,
            title = { Text("完全重置？", fontWeight = FontWeight.SemiBold) },
            text = { Text("将清空设置、观看进度、历史、收藏、搜索记录和信息流缓存，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.resetAll()
                }) { Text("重置", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("取消", color = Subtitle) }
            },
        )
    }
}