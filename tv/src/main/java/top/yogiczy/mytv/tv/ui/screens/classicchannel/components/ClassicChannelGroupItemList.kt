package top.yogiczy.mytv.tv.ui.screens.classicchannel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroup
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.tv.ui.material.rememberDebounceState
import top.yogiczy.mytv.tv.ui.screens.classicchannel.ClassicPanelScreenSystemAppsGroup
import top.yogiczy.mytv.tv.ui.screens.settings.LocalSettings
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunchedSaveable
import top.yogiczy.mytv.tv.ui.utils.getOrNullAt
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse
import top.yogiczy.mytv.tv.ui.utils.saveFocusRestorer
import top.yogiczy.mytv.tv.ui.utils.saveRequestFocus
import kotlin.math.max

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ClassicChannelGroupItemList(
    modifier: Modifier = Modifier,
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    initialChannelGroupProvider: () -> ChannelGroup = { ChannelGroup() },
    onChannelGroupFocused: (ChannelGroup) -> Unit = {},
    onUserAction: () -> Unit = {},
) {
    val channelGroupList = channelGroupListProvider()
    val initialChannelGroup = initialChannelGroupProvider()

    // 分组栏每次重组都会重新构造 ChannelGroupList，若跟着重建 FocusRequester，
    // 滚动时正在生效的实例会被不断替换，焦点容易丢、也容易踩到重新绑定过程中的空档。
    // 这里按「分组名序列」缓存：名字不变就复用同一批 FocusRequester。
    val channelGroupNames = channelGroupList.map { it.name }
    val itemFocusRequesterList =
        remember(channelGroupNames) { List(channelGroupNames.size) { FocusRequester() } }

    var focusedChannelGroup by remember { mutableStateOf(initialChannelGroup) }

    // 分组列表被整体重建（例如线路测速完成后替换整份列表，列表里的对象全是新的）时，
    // 记住的那个分组对象在新列表里已经找不到；按名字重新绑定一次，
    // 否则后续按下标取 FocusRequester 会拿到 -1（旧的崩溃点）。
    LaunchedEffect(channelGroupList) {
        if (channelGroupList.any { it.name == focusedChannelGroup.name }) return@LaunchedEffect
        channelGroupList.firstOrNull { it.name == initialChannelGroup.name }
            ?.let { focusedChannelGroup = it }
    }

    val listState = rememberLazyListState(max(0, channelGroupList.indexOf(initialChannelGroup) - 2))
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { _ -> onUserAction() }
    }

    val onChannelGroupFocusedDebounce = rememberDebounceState(wait = 100L) {
        onChannelGroupFocused(focusedChannelGroup)
    }

    val coroutineScope = rememberCoroutineScope()
    val firstFocusRequester = remember { FocusRequester() }
    val lastFocusRequester = remember { FocusRequester() }
    fun scrollToFirst() {
        if (channelGroupList.isEmpty()) return
        coroutineScope.launch {
            listState.scrollToItem(0)
            firstFocusRequester.saveRequestFocus()
        }
    }

    fun scrollToLast() {
        if (channelGroupList.isEmpty()) return
        coroutineScope.launch {
            listState.scrollToItem(channelGroupList.lastIndex)
            lastFocusRequester.saveRequestFocus()
        }
    }

    LazyColumn(
        modifier = modifier
            .width(140.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(0.9f))
            .ifElse(
                LocalSettings.current.uiFocusOptimize,
                Modifier.saveFocusRestorer {
                    // 按名字找，找不到就交给默认焦点（不再有 -1 下标越界）
                    val idx = channelGroupList.indexOfFirst { it.name == focusedChannelGroup.name }
                    itemFocusRequesterList.getOrNullAt(idx) ?: FocusRequester.Default
                },
            ),
        state = listState,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(channelGroupList) { index, channelGroup ->
            val isSelected by remember { derivedStateOf { channelGroup == focusedChannelGroup } }

            ClassicChannelGroupItem(
                modifier = Modifier
                    .ifElse(channelGroup == initialChannelGroup, Modifier.focusOnLaunchedSaveable())
                    .focusRequester(itemFocusRequesterList[index])
                    .ifElse(
                        index == 0,
                        Modifier
                            .focusRequester(firstFocusRequester)
                            .handleKeyEvents(onUp = { scrollToLast() })
                    )
                    .ifElse(
                        index == channelGroupList.lastIndex,
                        Modifier
                            .focusRequester(lastFocusRequester)
                            .handleKeyEvents(onDown = { scrollToFirst() })
                    ),
                channelGroupProvider = { channelGroup },
                isSelectedProvider = { isSelected },
                onFocused = {
                    focusedChannelGroup = channelGroup
                    onChannelGroupFocusedDebounce.send()
                },
            )
        }
    }
}

@Composable
private fun ClassicChannelGroupItem(
    modifier: Modifier = Modifier,
    channelGroupProvider: () -> ChannelGroup = { ChannelGroup() },
    isSelectedProvider: () -> Boolean = { false },
    onFocused: () -> Unit = {},
) {
    val channelGroup = channelGroupProvider()

    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    DenseListItem(
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (isFocused) onFocused()
            }
            .handleKeyEvents(
                isFocused = { isFocused },
                focusRequester = focusRequester,
                onSelect = {},
            ),
        colors = ListItemDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            selectedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        selected = isSelectedProvider(),
        onClick = {},
        leadingContent = if (channelGroup == ClassicPanelScreenSystemAppsGroup) {
            {
                Icon(
                    Icons.Default.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else null,
        headlineContent = {
            Text(
                text = channelGroup.name,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .ifElse(isFocused, Modifier.basicMarquee()),
            )
        },
    )
}

@Preview
@Composable
private fun ClassicChannelGroupItemListPreview() {
    MyTVTheme {
        ClassicChannelGroupItemList(
            channelGroupListProvider = { ChannelGroupList.EXAMPLE },
            initialChannelGroupProvider = { ChannelGroupList.EXAMPLE.first() },
        )
    }
}