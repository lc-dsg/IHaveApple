package com.ihaveapple.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihaveapple.app.data.Drama
import com.ihaveapple.app.data.ProgressRecord
import com.ihaveapple.app.player.LibraryTab
import com.ihaveapple.app.player.PlayerViewModel
import com.ihaveapple.app.player.UiState
import com.ihaveapple.app.util.formatRelativeTime

@Composable
fun LibraryOverlay(
    state: UiState,
    vm: PlayerViewModel,
) {
    AnimatedVisibility(visible = state.libraryTab != null, enter = fadeIn(), exit = fadeOut()) {
        val tab = state.libraryTab ?: LibraryTab.Search
        var confirmClear by remember(tab) { mutableStateOf<LibraryTab?>(null) }
        Column(
            Modifier
                .fillMaxSize()
                .background(ScrimDeep)
                .padding(top = 8.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "返回", onClick = vm::closeLibrary)
                Spacer(Modifier.width(SpaceXs))
                Text("资料库", color = Subtitle, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (tab == LibraryTab.History && state.history.isNotEmpty()) {
                    IconBtn(Icons.Filled.DeleteOutline, "清空观看历史") { confirmClear = LibraryTab.History }
                }
                if (tab == LibraryTab.Favorites && state.favorites.isNotEmpty()) {
                    IconBtn(Icons.Filled.DeleteOutline, "清空收藏") { confirmClear = LibraryTab.Favorites }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpaceMd, vertical = SpaceSm),
                horizontalArrangement = Arrangement.spacedBy(SpaceSm),
            ) {
                TabPill("搜索", tab == LibraryTab.Search) { vm.openLibrary(LibraryTab.Search) }
                TabPill("观看历史", tab == LibraryTab.History) { vm.openLibrary(LibraryTab.History) }
                TabPill("收藏夹", tab == LibraryTab.Favorites) { vm.openLibrary(LibraryTab.Favorites) }
            }

            when (tab) {
                LibraryTab.Search -> SearchPane(state, vm)
                LibraryTab.History -> RecordList(
                    records = state.history,
                    empty = "还没有观看历史\n从信息流滑到感兴趣的剧，点「进入查看全剧」就会记在这里",
                    showFavorite = false,
                    onOpen = vm::openRecord,
                    onUnfavorite = {},
                )
                LibraryTab.Favorites -> RecordList(
                    records = state.favorites,
                    empty = "收藏夹是空的\n在播放页点星标就能收藏，之后从这里接着看",
                    showFavorite = true,
                    onOpen = vm::openRecord,
                    onUnfavorite = { rec -> vm.removeFavorite(rec.seriesId) },
                )
            }
        }

        val pending = confirmClear
        if (pending != null) {
            val history = pending == LibraryTab.History
            AlertDialog(
                onDismissRequest = { confirmClear = null },
                containerColor = Proscenium,
                titleContentColor = Subtitle,
                textContentColor = Aside,
                title = {
                    Text(
                        if (history) "清空观看历史？" else "清空收藏？",
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                },
                text = {
                    Text(if (history) "所有剧的观看进度会一起清掉，无法恢复。" else "收藏夹会被清空，无法恢复。")
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (history) vm.clearHistory() else vm.clearFavorites()
                        confirmClear = null
                    }) { Text("清空", color = Danger) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = null }) { Text("取消", color = Subtitle) }
                },
            )
        }
    }
}

@Composable
private fun TabPill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(32.dp)
            .clip(PillShape)
            .background(if (active) Subtitle14 else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = SpaceMd),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) Subtitle else Aside, fontSize = 13.sp)
    }
}

@Composable
private fun SearchPane(state: UiState, vm: PlayerViewModel) {
    var query by rememberSaveable { mutableStateOf(state.searchQuery) }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(Modifier.fillMaxSize().padding(horizontal = SpaceMd)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(PillShape)
                .background(Subtitle08)
                .padding(horizontal = SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = Aside, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(SpaceSm))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("搜索短剧", color = Aside, fontSize = 14.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = TextStyle(color = Subtitle, fontSize = 14.sp),
                    singleLine = true,
                    cursorBrush = SolidColor(Crabapple),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        vm.submitSearch(query)
                    }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Text(
                    "搜索",
                    color = Crabapple,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable {
                            keyboard?.hide()
                            vm.submitSearch(query)
                        }
                        .padding(start = SpaceMd, top = SpaceSm, bottom = SpaceSm),
                )
            }
        }

        if (state.searches.isNotEmpty() && state.searchResults.isEmpty() && !state.searching) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = SpaceMd, bottom = SpaceXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("最近搜索", color = Aside, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "清空",
                    color = Aside,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { vm.clearSearches() },
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                items(state.searches) { word ->
                    Text(
                        word,
                        color = Subtitle,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { query = word; vm.submitSearch(word) }
                            .padding(vertical = SpaceMd),
                    )
                }
            }
        } else {
            Box(Modifier.weight(1f)) {
                when {
                    state.searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Crabapple, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                    state.searchError != null -> EmptyHint(state.searchError, Modifier.fillMaxSize())
                    state.searchResults.isEmpty() && state.searchQuery.isNotBlank() -> EmptyHint("没有找到相关短剧", Modifier.fillMaxSize())
                    state.searchResults.isEmpty() -> EmptyHint("输入剧名，找一部想看的", Modifier.fillMaxSize())
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(state.searchResults, key = { it.id }) { drama ->
                            DramaRow(drama) { vm.openSearchResult(drama) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DramaRow(drama: Drama, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(
            drama.coverUrl,
            Modifier
                .width(LayoutCoverThumbWidth)
                .height(LayoutCoverThumbHeight),
        )
        Spacer(Modifier.width(SpaceMd))
        Column(Modifier.weight(1f)) {
            Text(
                drama.title,
                color = Subtitle,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (drama.description.isNotBlank()) {
                Text(
                    drama.description,
                    color = Aside,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (drama.episodeCount > 0) {
                Text("全 ${drama.episodeCount} 集", color = Aside.copy(alpha = 0.8f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun RecordList(
    records: List<ProgressRecord>,
    empty: String,
    showFavorite: Boolean,
    onOpen: (ProgressRecord) -> Unit,
    onUnfavorite: (ProgressRecord) -> Unit,
) {
    if (records.isEmpty()) {
        EmptyHint(empty, Modifier.fillMaxSize())
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = SpaceMd)) {
        items(records, key = { it.seriesId }) { rec ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(rec) }
                    .padding(vertical = SpaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(
                    rec.coverUrl,
                    Modifier
                        .width(LayoutCoverThumbWidth)
                        .height(LayoutCoverThumbHeight),
                )
                Spacer(Modifier.width(SpaceMd))
                Column(Modifier.weight(1f)) {
                    Text(rec.title, color = Subtitle, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "看到第 ${rec.episodeNumber} 集 · ${(rec.progressPercent * 100).toInt()}%" +
                            if (rec.total > 0) " · 共 ${rec.total} 集" else "",
                        color = Aside,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatRelativeTime(rec.updatedAt), color = Aside.copy(alpha = 0.7f), fontSize = 11.sp)
                        Spacer(Modifier.width(SpaceSm))
                        Box(
                            Modifier
                                .width(80.dp)
                                .height(2.dp)
                                .background(Subtitle14)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(rec.progressPercent.toFloat())
                                    .height(2.dp)
                                    .background(Crabapple)
                            )
                        }
                    }
                }
                if (showFavorite) {
                    IconBtn(
                        if (rec.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        "取消收藏",
                        active = rec.favorite,
                        size = 36.dp,
                    ) { onUnfavorite(rec) }
                }
            }
            RowDivider()
        }
    }
}