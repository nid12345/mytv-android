package top.yogiczy.mytv.tv.ui.screens.channelgroup

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.tv.ui.material.Drawer
import top.yogiczy.mytv.tv.ui.material.DrawerPosition
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunchedSaveable
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse

/**
 * 全部分组管理：把所有订阅源的分组集中到一个界面里统一设定显示/隐藏
 *
 * 各订阅源的分组名来自缓存（应用里加载过该订阅源后就会有），
 * 显隐设定按「订阅源地址 + 分组名」分别记忆，切换源、重启后保持。
 */
@Composable
fun SourceGroupManageScreen(
    modifier: Modifier = Modifier,
    sourcesProvider: () -> List<IptvSource> = { emptyList() },
    hiddenMapProvider: () -> Map<String, List<String>> = { emptyMap() },
    groupNamesMapProvider: () -> Map<String, List<String>> = { emptyMap() },
    onToggleGroup: (sourceUrl: String, group: String) -> Unit = { _, _ -> },
    onClose: () -> Unit = {},
) {
    val sources = sourcesProvider()

    Drawer(
        position = DrawerPosition.Bottom,
        onDismissRequest = onClose,
        header = { Text("全部分组管理") },
    ) {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            sources.forEach { source ->
                item(key = source.url) {
                    Text(
                        text = "${source.name}　${source.url}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp, start = 8.dp)
                            .basicMarquee(),
                    )
                }

                val groups = groupNamesMapProvider()[source.url]
                if (groups.isNullOrEmpty()) {
                    item(key = "${source.url}-empty") {
                        Text(
                            text = "（尚未加载过该订阅源，打开一次后可管理分组）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    itemsIndexed(groups) { index, group ->
                        SourceGroupManageItem(
                            modifier = Modifier.ifElse(
                                index == 0 && sources.first() == source,
                                Modifier.focusOnLaunchedSaveable(),
                            ),
                            groupProvider = { group },
                            isHiddenProvider = {
                                hiddenMapProvider()[source.url]?.contains(group) ?: false
                            },
                            onToggleHidden = { onToggleGroup(source.url, group) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceGroupManageItem(
    modifier: Modifier = Modifier,
    groupProvider: () -> String = { "" },
    isHiddenProvider: () -> Boolean = { false },
    onToggleHidden: () -> Unit = {},
) {
    val group = groupProvider()
    val isHidden = isHiddenProvider()

    var isFocused by remember { mutableStateOf(false) }

    ListItem(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .handleKeyEvents(onSelect = onToggleHidden),
        colors = ListItemDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
        ),
        selected = false,
        headlineContent = {
            Text(
                text = group,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.ifElse(isFocused, Modifier.basicMarquee()),
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (isHidden) "已隐藏" else "显示中",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(if (isHidden) 0.4f else 0.8f),
                )
                if (isHidden) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        },
        onClick = {},
    )
}
