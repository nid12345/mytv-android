package top.yogiczy.mytv.tv.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.util.utils.humanizeMs
import top.yogiczy.mytv.tv.ui.material.LocalPopupManager
import top.yogiczy.mytv.tv.ui.material.SimplePopup
import top.yogiczy.mytv.tv.ui.material.Tag
import top.yogiczy.mytv.tv.ui.screens.channelgroup.ChannelGroupManageScreen
import top.yogiczy.mytv.tv.ui.screens.channelgroup.SourceGroupManageScreen
import top.yogiczy.mytv.tv.ui.screens.components.SelectDialog
import top.yogiczy.mytv.tv.ui.screens.iptvsource.IptvSourceScreen
import top.yogiczy.mytv.tv.ui.screens.main.MainViewModel
import top.yogiczy.mytv.tv.ui.screens.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.utils.Configs

@Composable
fun SettingsCategoryIptv(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = viewModel(),
    mainViewModel: MainViewModel = viewModel(),
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
) {
    val coroutineScope = rememberCoroutineScope()

    SettingsContentList(modifier) {
        item {
            SettingsListItem(
                modifier = Modifier.focusRequester(it),
                headlineContent = "数字选台",
                supportingContent = "通过数字选择频道",
                trailingContent = {
                    Switch(settingsViewModel.iptvChannelNoSelectEnable, null)
                },
                onSelected = {
                    settingsViewModel.iptvChannelNoSelectEnable =
                        !settingsViewModel.iptvChannelNoSelectEnable
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "直播源快速切换",
                supportingContent = if (settingsViewModel.iptvSourceQuickSwitchEnable)
                    "频道分组栏顶部显示「换源」入口，可直接切换订阅链接"
                else "已隐藏「换源」入口",
                trailingContent = {
                    Switch(settingsViewModel.iptvSourceQuickSwitchEnable, null)
                },
                onSelected = {
                    settingsViewModel.iptvSourceQuickSwitchEnable =
                        !settingsViewModel.iptvSourceQuickSwitchEnable
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "记住上次频道",
                supportingContent = if (settingsViewModel.iptvLastChannelRememberEnable)
                    "打开应用时自动播放上次退出时的频道"
                else "已关闭，打开应用时从第一个频道开始",
                trailingContent = {
                    Switch(settingsViewModel.iptvLastChannelRememberEnable, null)
                },
                onSelected = {
                    settingsViewModel.iptvLastChannelRememberEnable =
                        !settingsViewModel.iptvLastChannelRememberEnable
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "线路测速排序",
                supportingContent = if (settingsViewModel.iptvSourceLineSpeedSortEnable)
                    "后台给默认直播源的各条线路测速，把最流畅的排到第一位（不影响其它订阅源）"
                else "已关闭，线路顺序保持直播源里的原始顺序",
                trailingContent = {
                    Switch(settingsViewModel.iptvSourceLineSpeedSortEnable, null)
                },
                onSelected = {
                    settingsViewModel.iptvSourceLineSpeedSortEnable =
                        !settingsViewModel.iptvSourceLineSpeedSortEnable
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "潮汕节目回放",
                supportingContent = if (settingsViewModel.iptvChaoshanSourceEnable)
                    "浏览默认直播源时，在分组末尾附加显示「潮汕节目回放」"
                else "已关闭（订阅地址已内置为独立订阅源，可在「换源」里直接切过去）",
                trailingContent = {
                    Switch(settingsViewModel.iptvChaoshanSourceEnable, null)
                },
                onSelected = {
                    settingsViewModel.iptvChaoshanSourceEnable =
                        !settingsViewModel.iptvChaoshanSourceEnable
                    // 当前正浏览默认源时立即重载频道列表；在其它源上则下次浏览默认源生效
                    val isDefaultSource =
                        Constants.normalizeIptvSource(settingsViewModel.iptvSourceCurrent).url ==
                                Constants.IPTV_SOURCE_LIST.first().url
                    if (isDefaultSource) mainViewModel.init()
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "自动连播",
                supportingContent = if (settingsViewModel.iptvVodAutoPlayNextEnable)
                    "「潮汕节目回放」等点播源：一个视频播完自动播下一个（同分组播完接下一分组）"
                else "已关闭，视频播完后停住",
                trailingContent = {
                    Switch(settingsViewModel.iptvVodAutoPlayNextEnable, null)
                },
                onSelected = {
                    settingsViewModel.iptvVodAutoPlayNextEnable =
                        !settingsViewModel.iptvVodAutoPlayNextEnable
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "换台反转",
                supportingContent = if (settingsViewModel.iptvChannelChangeFlip) "方向键上：下一个频道；方向键下：上一个频道"
                else "方向键上：上一个频道；方向键下：下一个频道",
                trailingContent = {
                    Switch(settingsViewModel.iptvChannelChangeFlip, null)
                },
                onSelected = {
                    settingsViewModel.iptvChannelChangeFlip =
                        !settingsViewModel.iptvChannelChangeFlip
                },
            )
        }

        item {
            val popupManager = LocalPopupManager.current
            val focusRequester = remember { FocusRequester() }
            var visible by remember { mutableStateOf(false) }

            SettingsListItem(
                modifier = Modifier.focusRequester(focusRequester),
                headlineContent = "直播源缓存时间",
                trailingContent = when (settingsViewModel.iptvSourceCacheTime) {
                    0L -> "不缓存"
                    Long.MAX_VALUE -> "永久"
                    else -> settingsViewModel.iptvSourceCacheTime.humanizeMs()
                },
                onSelected = {
                    popupManager.push(focusRequester, true)
                    visible = true
                },
                remoteConfig = true,
            )

            SelectDialog(
                visibleProvider = { visible },
                onDismissRequest = { visible = false },
                title = "直播源缓存时间",
                currentDataProvider = { settingsViewModel.iptvSourceCacheTime },
                dataListProvider = {
                    (0..<24).map { it * 1000L * 60 * 60 }
                        .plus((1..15).map { it * 1000L * 60 * 60 * 24 })
                        .plus(listOf(Long.MAX_VALUE))
                },
                dataText = {
                    when (it) {
                        0L -> "不缓存"
                        Long.MAX_VALUE -> "永久"
                        else -> it.humanizeMs()
                    }
                },
                onDataSelected = {
                    settingsViewModel.iptvSourceCacheTime = it
                    visible = false
                },
            )
        }

        item {
            val popupManager = LocalPopupManager.current
            val focusRequester = remember { FocusRequester() }
            val currentIptvSource = settingsViewModel.iptvSourceCurrent
            var isIptvSourceScreenVisible by remember { mutableStateOf(false) }

            SettingsListItem(
                modifier = Modifier.focusRequester(focusRequester),
                headlineContent = "自定义直播源",
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Tag(if (currentIptvSource.isLocal) "本地" else "远程")
                        Text(currentIptvSource.name)
                    }
                },
                onSelected = {
                    popupManager.push(focusRequester, true)
                    isIptvSourceScreenVisible = true
                },
                remoteConfig = true,
            )

            SimplePopup(
                visibleProvider = { isIptvSourceScreenVisible },
                onDismissRequest = { isIptvSourceScreenVisible = false },
            ) {
                IptvSourceScreen(
                    iptvSourceListProvider = { settingsViewModel.iptvSourceList },
                    currentIptvSourceProvider = { settingsViewModel.iptvSourceCurrent },
                    onIptvSourceSelected = {
                        isIptvSourceScreenVisible = false
                        if (settingsViewModel.iptvSourceCurrent != it) {
                            settingsViewModel.iptvSourceCurrent = it
                            settingsViewModel.iptvLastChannelIdx = 0
                            // 先清缓存再重载（分组显隐按订阅源分别记忆，切换源不需要清理）
                            coroutineScope.launch {
                                IptvRepository(it).clearCache()
                                mainViewModel.init()
                            }
                        }
                    },
                    onIptvSourceDeleted = {
                        settingsViewModel.iptvSourceList =
                            IptvSourceList(settingsViewModel.iptvSourceList - it)
                    },
                    onIptvSourceAdded = { newSource ->
                        isIptvSourceScreenVisible = false
                        settingsViewModel.iptvSourceList =
                            IptvSourceList(settingsViewModel.iptvSourceList + newSource)
                        settingsViewModel.iptvSourceCurrent = newSource
                        settingsViewModel.iptvLastChannelIdx = 0
                        coroutineScope.launch {
                            IptvRepository(newSource).clearCache()
                            mainViewModel.init()
                        }
                    },
                )
            }
        }

        item {
            val popupManager = LocalPopupManager.current
            val focusRequester = remember { FocusRequester() }
            var visible by remember { mutableStateOf(false) }

            SettingsListItem(
                modifier = Modifier.focusRequester(focusRequester),
                headlineContent = "频道分组管理",
                supportingContent = "管理频道分组可见、隐藏状态",
                onSelected = {
                    popupManager.push(focusRequester, true)
                    visible = true
                },
                remoteConfig = true,
            )

            SimplePopup(
                visibleProvider = { visible },
                onDismissRequest = { visible = false },
            ) {
                ChannelGroupManageScreen(
                    channelGroupListProvider = {
                        channelGroupListProvider().map { it.name }.toPersistentList()
                    },
                    channelGroupHiddenListProvider = { settingsViewModel.iptvChannelGroupHiddenList.toPersistentList() },
                    onChannelGroupHiddenListChange = {
                        settingsViewModel.iptvChannelGroupHiddenList = it.toSet()
                    },
                    onClose = { visible = false },
                )
            }
        }

        item {
            val popupManager = LocalPopupManager.current
            val focusRequester = remember { FocusRequester() }
            var visible by remember { mutableStateOf(false) }

            SettingsListItem(
                modifier = Modifier.focusRequester(focusRequester),
                headlineContent = "全部分组管理",
                supportingContent = "统一管理所有订阅源的分组显示、隐藏（切换源、重启后保持）",
                onSelected = {
                    popupManager.push(focusRequester, true)
                    visible = true
                },
                remoteConfig = true,
            )

            SimplePopup(
                visibleProvider = { visible },
                onDismissRequest = { visible = false },
            ) {
                SourceGroupManageScreen(
                    sourcesProvider = {
                        (Constants.IPTV_SOURCE_LIST + settingsViewModel.iptvSourceList)
                            .distinctBy { it.url }
                    },
                    hiddenMapProvider = { settingsViewModel.iptvSourceGroupHiddenMap },
                    groupNamesMapProvider = { Configs.iptvSourceGroupNamesMap },
                    onToggleGroup = { sourceUrl, group ->
                        settingsViewModel.toggleSourceGroupHidden(sourceUrl, group)
                    },
                    onClose = { visible = false },
                )
            }
        }

        item {
            SettingsListItem(
                headlineContent = "混合模式",
                supportingContent = when (settingsViewModel.iptvHybridMode) {
                    Configs.IptvHybridMode.DISABLE -> ""
                    Configs.IptvHybridMode.IPTV_FIRST -> "优先尝试播放直播源中线路，若所有直播源线路不可用，则进入混合模式"
                    Configs.IptvHybridMode.HYBRID_FIRST -> "优先进入混合模式，若混合模式不可用，则播放直播源中线路"
                },
                trailingContent = when (settingsViewModel.iptvHybridMode) {
                    Configs.IptvHybridMode.DISABLE -> "禁用"
                    Configs.IptvHybridMode.IPTV_FIRST -> "直播源优先"
                    Configs.IptvHybridMode.HYBRID_FIRST -> "混合优先"
                },
                onSelected = {
                    settingsViewModel.iptvHybridMode =
                        Configs.IptvHybridMode.entries.let { it[(it.indexOf(settingsViewModel.iptvHybridMode) + 1) % it.size] }
                },
            )
        }
    }
}