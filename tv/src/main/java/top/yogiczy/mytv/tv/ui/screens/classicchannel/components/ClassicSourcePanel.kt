package top.yogiczy.mytv.tv.ui.screens.classicchannel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.tv.ui.material.LocalPopupManager
import top.yogiczy.mytv.tv.ui.material.SimplePopup
import top.yogiczy.mytv.tv.ui.screens.iptvsource.components.AddIptvSourceDialog
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse
import kotlin.math.max

/**
 * 「换源」面板
 *
 * 在经典选台界面里，左侧分组栏选中「换源」时，右侧展示这个订阅列表：
 * 直接选一个订阅即可切换直播源，最后一项可以新增订阅链接。
 */
@Composable
fun ClassicSourcePanel(
    modifier: Modifier = Modifier,
    iptvSourceListProvider: () -> IptvSourceList = { IptvSourceList() },
    currentIptvSourceProvider: () -> IptvSource = { IptvSource() },
    onIptvSourceSelected: (IptvSource) -> Unit = {},
    onIptvSourceDeleted: (IptvSource) -> Unit = {},
    onIptvSourceAdded: (IptvSource) -> Unit = {},
    onUserAction: () -> Unit = {},
) {
    // 内置直播源排在前面，用户自定义的排在后面，与设置里的「自定义直播源」保持一致
    val iptvSourceList = iptvSourceListProvider().let { Constants.IPTV_SOURCE_LIST + it }
    val currentIptvSource = currentIptvSourceProvider()
    val currentIdx = iptvSourceList.indexOf(currentIptvSource)

    val listState = rememberLazyListState(max(0, currentIdx - 2))

    LazyColumn(
        modifier = modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(0.8f)),
        state = listState,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                text = "选择订阅",
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            )
        }

        itemsIndexed(iptvSourceList) { index, source ->
            ClassicSourceItem(
                sourceProvider = { source },
                isSelectedProvider = { index == currentIdx },
                onFocused = { onUserAction() },
                onSelected = { onIptvSourceSelected(source) },
                onDeleted = { onIptvSourceDeleted(source) },
            )
        }

        item {
            val popupManager = LocalPopupManager.current
            val focusRequester = remember { FocusRequester() }
            var isFocused by remember { mutableStateOf(false) }
            var showAdd by remember { mutableStateOf(false) }

            DenseListItem(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        isFocused = it.isFocused || it.hasFocus
                        if (isFocused) onUserAction()
                    }
                    .handleKeyEvents(
                        isFocused = { isFocused },
                        focusRequester = focusRequester,
                        onSelect = {
                            popupManager.push(focusRequester, true)
                            showAdd = true
                        },
                    ),
                selected = false,
                onClick = {},
                headlineContent = {
                    Text(
                        text = "＋ 添加订阅链接",
                        maxLines = 1,
                        modifier = Modifier.ifElse(isFocused, Modifier.basicMarquee()),
                    )
                },
            )

            SimplePopup(
                visibleProvider = { showAdd },
                onDismissRequest = { showAdd = false },
            ) {
                AddIptvSourceDialog(
                    onConfirm = { name, url ->
                        showAdd = false
                        onIptvSourceAdded(IptvSource(name = name, url = url))
                    },
                )
            }
        }
    }
}

@Composable
private fun ClassicSourceItem(
    modifier: Modifier = Modifier,
    sourceProvider: () -> IptvSource = { IptvSource() },
    isSelectedProvider: () -> Boolean = { false },
    onFocused: () -> Unit = {},
    onSelected: () -> Unit = {},
    onDeleted: () -> Unit = {},
) {
    val source = sourceProvider()
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
                onSelect = onSelected,
                onLongSelect = {
                    // 内置源不允许删除
                    if (source !in Constants.IPTV_SOURCE_LIST) onDeleted()
                },
            ),
        colors = ListItemDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            selectedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        selected = isSelectedProvider(),
        onClick = {},
        headlineContent = {
            Text(
                text = source.name,
                maxLines = 1,
                modifier = Modifier.ifElse(isFocused, Modifier.basicMarquee()),
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelectedProvider()) {
                    Text(
                        text = "使用中",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        },
    )
}

@Preview
@Composable
private fun ClassicSourcePanelPreview() {
    MyTVTheme {
        ClassicSourcePanel(
            iptvSourceListProvider = {
                IptvSourceList(
                    listOf(
                        IptvSource(name = "我的订阅", url = "http://1.2.3.4/iptv.txt"),
                    )
                )
            },
            currentIptvSourceProvider = { Constants.IPTV_SOURCE_LIST.first() },
        )
    }
}
