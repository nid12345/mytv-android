package top.yogiczy.mytv.tv.ui.screens.main.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelIdx
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.yogiczy.mytv.core.data.entities.epg.Epg
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.epg.EpgList.Companion.match
import top.yogiczy.mytv.core.data.entities.epg.EpgList.Companion.recentProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserveList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.core.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.core.data.utils.ChannelUtil
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.tv.ui.material.PopupContent
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.Visible
import top.yogiczy.mytv.tv.ui.material.popupable
import top.yogiczy.mytv.tv.ui.screens.applauncher.launchApp
import top.yogiczy.mytv.tv.ui.screens.channel.ChannelNumberSelectScreen
import top.yogiczy.mytv.tv.ui.screens.channel.ChannelScreen
import top.yogiczy.mytv.tv.ui.screens.channel.ChannelTempScreen
import top.yogiczy.mytv.tv.ui.screens.channel.rememberChannelNumberSelectState
import top.yogiczy.mytv.tv.ui.screens.channelurl.ChannelUrlScreen
import top.yogiczy.mytv.tv.ui.screens.classicchannel.ClassicChannelScreen
import top.yogiczy.mytv.tv.ui.screens.datetime.DatetimeScreen
import top.yogiczy.mytv.tv.ui.screens.dlna.DlnaCastScreen
import top.yogiczy.mytv.tv.ui.screens.epg.EpgProgrammeProgressScreen
import top.yogiczy.mytv.tv.ui.screens.epg.EpgScreen
import top.yogiczy.mytv.tv.ui.screens.epgreverse.EpgReverseScreen
import top.yogiczy.mytv.tv.ui.screens.main.MainViewModel
import top.yogiczy.mytv.tv.ui.screens.monitor.MonitorScreen
import top.yogiczy.mytv.tv.ui.screens.quickop.QuickOpScreen
import top.yogiczy.mytv.tv.ui.screens.settings.SettingsScreen
import top.yogiczy.mytv.tv.ui.screens.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screens.update.UpdateScreen
import top.yogiczy.mytv.tv.ui.screens.videoplayer.VideoPlayerScreen
import top.yogiczy.mytv.tv.ui.screens.videoplayer.rememberVideoPlayerState
import top.yogiczy.mytv.tv.ui.screens.videoplayercontroller.VideoPlayerControllerScreen
import top.yogiczy.mytv.tv.ui.screens.videoplayerdiaplaymode.VideoPlayerDisplayModeScreen
import top.yogiczy.mytv.tv.ui.screens.webview.WebViewScreen
import top.yogiczy.mytv.tv.ui.utils.Configs
import top.yogiczy.mytv.tv.ui.utils.captureBackKey
import top.yogiczy.mytv.tv.ui.utils.handleDragGestures
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    filteredChannelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    epgListProvider: () -> EpgList = { EpgList() },
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val coroutineScope = rememberCoroutineScope()
    val mainViewModel: MainViewModel = viewModel()
    val context = LocalContext.current

    // 当前订阅源是「潮汕节目回放」时不显示台标位（那是点播回看列表，没有台标），
    // 空出来的横向空间留给较长的标题
    val showChannelLogoProvider = {
        settingsViewModel.uiShowChannelLogo &&
                !Constants.isChaoshanReplaySource(settingsViewModel.iptvSourceCurrent)
    }

    // 显示模式按「订阅源/频道」记忆：播放器 ready 时按当前频道解析实际生效的比例。
    // provider 在状态创建前就要传入，这里用 holder 在组合后回填当前频道。
    val currentChannelHolder = remember { mutableStateOf<() -> Channel>({ Channel() }) }

    val videoPlayerState =
        rememberVideoPlayerState(defaultDisplayModeProvider = {
            Configs.resolveVideoPlayerDisplayMode(currentChannelHolder.value.invoke())
        })
    val mainContentState = rememberMainContentState(
        videoPlayerState = videoPlayerState,
        channelGroupListProvider = filteredChannelGroupListProvider,
    )
    SideEffect { currentChannelHolder.value = { mainContentState.currentChannel } }
    val channelNumberSelectState = rememberChannelNumberSelectState {
        val idx = it.toInt() - 1
        filteredChannelGroupListProvider().channelList.getOrNull(idx)?.let { channel ->
            mainContentState.changeCurrentChannel(channel)
        }
    }

    // 线路测速会在后台重排 urlList，列表一变化就把当前频道重新绑到新对象上
    val currentChannelGroupList = filteredChannelGroupListProvider()
    LaunchedEffect(currentChannelGroupList) {
        mainContentState.onChannelGroupListChanged()
        // 收藏是跨订阅源的：当前源里出现收藏过的频道时，把快照里的线路地址刷新成最新的
        settingsViewModel.syncFavoriteChannels(currentChannelGroupList.channelList)
    }

    Box(
        modifier = modifier
            .popupable()
            .captureBackKey { onBackPressed() }
            .handleKeyEvents(
                onUp = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToNext()
                    else mainContentState.changeCurrentChannelToPrev()
                },
                onDown = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToPrev()
                    else mainContentState.changeCurrentChannelToNext()
                },
                onLeft = {
                    if (mainContentState.currentChannel.urlList.size > 1) {
                        mainContentState.changeCurrentChannel(
                            mainContentState.currentChannel,
                            mainContentState.currentChannelUrlIdx - 1,
                        )
                    }
                },
                onRight = {
                    if (mainContentState.currentChannel.urlList.size > 1) {
                        mainContentState.changeCurrentChannel(
                            mainContentState.currentChannel,
                            mainContentState.currentChannelUrlIdx + 1,
                        )
                    }
                },
                onSelect = { mainContentState.isChannelScreenVisible = true },
                onLongSelect = { mainContentState.isQuickOpScreenVisible = true },
                onSettings = { mainContentState.isQuickOpScreenVisible = true },
                onLongLeft = { mainContentState.isEpgScreenVisible = true },
                onLongRight = { mainContentState.isChannelUrlScreenVisible = true },
                onLongDown = { mainContentState.isVideoPlayerControllerScreenVisible = true },
                onNumber = { channelNumberSelectState.input(it) },
            )
            .handleDragGestures(
                onSwipeDown = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToNext()
                    else mainContentState.changeCurrentChannelToPrev()
                },
                onSwipeUp = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToPrev()
                    else mainContentState.changeCurrentChannelToNext()
                },
                onSwipeRight = {
                    if (mainContentState.currentChannel.urlList.size > 1) {
                        mainContentState.changeCurrentChannel(
                            mainContentState.currentChannel,
                            mainContentState.currentChannelUrlIdx - 1,
                        )
                    }
                },
                onSwipeLeft = {
                    if (mainContentState.currentChannel.urlList.size > 1) {
                        mainContentState.changeCurrentChannel(
                            mainContentState.currentChannel,
                            mainContentState.currentChannelUrlIdx + 1,
                        )
                    }
                },
            ),
    ) {
        VideoPlayerScreen(
            state = videoPlayerState,
            showMetadataProvider = { settingsViewModel.debugShowVideoPlayerMetadata },
        )

        Visible({ ChannelUtil.isHybridWebViewUrl(mainContentState.currentChannel.urlList[mainContentState.currentChannelUrlIdx]) }) {
            WebViewScreen(
                urlProvider = { mainContentState.currentChannel.urlList[mainContentState.currentChannelUrlIdx] },
                onVideoResolutionChanged = { width, height ->
                    videoPlayerState.metadata = videoPlayerState.metadata.copy(
                        videoWidth = width,
                        videoHeight = height,
                    )
                    mainContentState.isTempChannelScreenVisible = false
                },
            )
        }
    }

    Visible({ settingsViewModel.uiShowEpgProgrammePermanentProgress }) {
        EpgProgrammeProgressScreen(
            currentEpgProgrammeProvider = {
                mainContentState.currentPlaybackEpgProgramme
                    ?: epgListProvider().recentProgramme(mainContentState.currentChannel)?.now
            },
            videoPlayerCurrentPositionProvider = { videoPlayerState.currentPosition },
        )
    }

    Visible({
        !mainContentState.isTempChannelScreenVisible
                && !mainContentState.isChannelScreenVisible
                && !mainContentState.isSettingsScreenVisible
                && !mainContentState.isQuickOpScreenVisible
                && !mainContentState.isEpgScreenVisible
                && !mainContentState.isChannelUrlScreenVisible
                && channelNumberSelectState.channelNumber.isEmpty()
    }) {
        DatetimeScreen(showModeProvider = { settingsViewModel.uiTimeShowMode })
    }

    ChannelNumberSelectScreen(channelNumberProvider = { channelNumberSelectState.channelNumber })

    Visible({
        mainContentState.isTempChannelScreenVisible
                && !mainContentState.isChannelScreenVisible
                && !mainContentState.isSettingsScreenVisible
                && !mainContentState.isQuickOpScreenVisible
                && !mainContentState.isEpgScreenVisible
                && !mainContentState.isChannelUrlScreenVisible
                && channelNumberSelectState.channelNumber.isEmpty()
    }) {
        ChannelTempScreen(
            channelProvider = { mainContentState.currentChannel },
            channelUrlIdxProvider = { mainContentState.currentChannelUrlIdx },
            channelNumberProvider = { filteredChannelGroupListProvider().channelIdx(mainContentState.currentChannel) + 1 },
            showChannelLogoProvider = showChannelLogoProvider,
            recentEpgProgrammeProvider = {
                epgListProvider().recentProgramme(mainContentState.currentChannel)
            },
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            videoPlayerMetadataProvider = { videoPlayerState.metadata },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isEpgScreenVisible },
        onDismissRequest = { mainContentState.isEpgScreenVisible = false },
    ) {
        EpgScreen(
            epgProvider = {
                epgListProvider().match(mainContentState.currentChannel)
                    ?: Epg.empty(mainContentState.currentChannel)
            },
            epgProgrammeReserveListProvider = {
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList.filter {
                    it.channel == mainContentState.currentChannel.name
                })
            },
            supportPlaybackProvider = { mainContentState.supportPlayback() },
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            onEpgProgrammePlayback = {
                mainContentState.isEpgScreenVisible = false
                mainContentState.changeCurrentChannel(
                    mainContentState.currentChannel,
                    mainContentState.currentChannelUrlIdx,
                    it,
                )
            },
            onEpgProgrammeReserve = { programme ->
                mainContentState.reverseEpgProgrammeOrNot(
                    mainContentState.currentChannel,
                    programme
                )
            },
            onClose = { mainContentState.isEpgScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isChannelUrlScreenVisible },
        onDismissRequest = { mainContentState.isChannelUrlScreenVisible = false },
    ) {
        ChannelUrlScreen(
            channelProvider = { mainContentState.currentChannel },
            currentUrlProvider = { mainContentState.currentChannel.urlList[mainContentState.currentChannelUrlIdx] },
            onUrlSelected = {
                mainContentState.isChannelUrlScreenVisible = false
                mainContentState.changeCurrentChannel(
                    mainContentState.currentChannel,
                    mainContentState.currentChannel.urlList.indexOf(it),
                )
            },
            onClose = { mainContentState.isChannelUrlScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isVideoPlayerControllerScreenVisible },
        onDismissRequest = { mainContentState.isVideoPlayerControllerScreenVisible = false },
    ) {
        val threshold = 1000L * 60 * 60 * 24 * 365
        val hour0 = -28800000L

        VideoPlayerControllerScreen(
            isVideoPlayerPlayingProvider = { videoPlayerState.isPlaying },
            isVideoPlayerBufferingProvider = { videoPlayerState.isBuffering },
            videoPlayerCurrentPositionProvider = {
                if (videoPlayerState.currentPosition >= threshold) videoPlayerState.currentPosition
                else hour0 + videoPlayerState.currentPosition
            },
            videoPlayerDurationProvider = {
                if (videoPlayerState.currentPosition >= threshold) {
                    val playback = mainContentState.currentPlaybackEpgProgramme

                    if (playback != null) {
                        playback.startAt to playback.endAt
                    } else {
                        val programme =
                            epgListProvider().recentProgramme(mainContentState.currentChannel)?.now
                        (programme?.startAt ?: hour0) to (programme?.endAt ?: hour0)
                    }
                } else {
                    hour0 to (hour0 + videoPlayerState.duration)
                }
            },
            onVideoPlayerPlay = { videoPlayerState.play() },
            onVideoPlayerPause = { videoPlayerState.pause() },
            onVideoPlayerSeekTo = { videoPlayerState.seekTo(it) },
            onClose = { mainContentState.isVideoPlayerControllerScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isVideoPlayerDisplayModeScreenVisible },
        onDismissRequest = { mainContentState.isVideoPlayerDisplayModeScreenVisible = false },
    ) {
        VideoPlayerDisplayModeScreen(
            currentDisplayModeProvider = { videoPlayerState.displayMode },
            onDisplayModeChanged = {
                videoPlayerState.displayMode = it
                // 只记住这个频道：下次再打开它时优先用这里设的比例，
                // 其它频道仍用全局默认（默认 16:9）
                settingsViewModel.rememberVideoPlayerDisplayMode(
                    it, mainContentState.currentChannel
                )
            },
            onApplyToGlobal = {
                mainContentState.isVideoPlayerDisplayModeScreenVisible = false
                settingsViewModel.videoPlayerDisplayMode = videoPlayerState.displayMode
                // 设为全局默认后，本频道的单独设置作废（否则它会一直盖住全局值）
                settingsViewModel.forgetVideoPlayerDisplayMode(mainContentState.currentChannel)
                Snackbar.show("已设为全局默认（本频道不再单独记忆）")
            },
            onClose = { mainContentState.isVideoPlayerDisplayModeScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isQuickOpScreenVisible },
        onDismissRequest = { mainContentState.isQuickOpScreenVisible = false },
    ) {
        QuickOpScreen(
            currentChannelProvider = { mainContentState.currentChannel },
            currentChannelUrlIdxProvider = { mainContentState.currentChannelUrlIdx },
            currentChannelNumberProvider = {
                (filteredChannelGroupListProvider().channelList.indexOf(mainContentState.currentChannel) + 1).toString()
            },
            showChannelLogoProvider = showChannelLogoProvider,
            epgListProvider = epgListProvider,
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            videoPlayerMetadataProvider = { videoPlayerState.metadata },
            onShowEpg = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isEpgScreenVisible = true
            },
            onShowChannelUrl = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isChannelUrlScreenVisible = true
            },
            onShowVideoPlayerController = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isVideoPlayerControllerScreenVisible = true
            },
            onShowVideoPlayerDisplayMode = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isVideoPlayerDisplayModeScreenVisible = true
            },
            onShowDlnaCast = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isDlnaCastScreenVisible = true
            },
            onShowMoreSettings = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isSettingsScreenVisible = true
            },
            onClearCache = {
                settingsViewModel.iptvPlayableHostList = emptySet()
                coroutineScope.launch {
                    IptvRepository(settingsViewModel.iptvSourceCurrent).clearCache()
                    EpgRepository(settingsViewModel.epgSourceCurrent).clearCache()
                    Snackbar.show("缓存已清除，请重启应用")
                }
            },
            onClose = { mainContentState.isQuickOpScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isDlnaCastScreenVisible },
        onDismissRequest = { mainContentState.isDlnaCastScreenVisible = false },
    ) {
        DlnaCastScreen(
            channelProvider = { mainContentState.currentChannel },
            channelUrlIdxProvider = { mainContentState.currentChannelUrlIdx },
            onClose = { mainContentState.isDlnaCastScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isChannelScreenVisible && !settingsViewModel.uiUseClassicPanelScreen },
        onDismissRequest = { mainContentState.isChannelScreenVisible = false },
    ) {
        ChannelScreen(
            channelGroupListProvider = filteredChannelGroupListProvider,
            currentChannelProvider = { mainContentState.currentChannel },
            currentChannelUrlIdxProvider = { mainContentState.currentChannelUrlIdx },
            showChannelLogoProvider = showChannelLogoProvider,
            onChannelSelected = {
                mainContentState.isChannelScreenVisible = false
                mainContentState.changeCurrentChannel(it)
            },
            onChannelFavoriteToggle = { mainContentState.favoriteChannelOrNot(it) },
            epgListProvider = epgListProvider,
            showEpgProgrammeProgressProvider = { settingsViewModel.uiShowEpgProgrammeProgress },
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            videoPlayerMetadataProvider = { videoPlayerState.metadata },
            channelFavoriteEnabledProvider = { settingsViewModel.iptvChannelFavoriteEnable },
            channelFavoriteListProvider = { settingsViewModel.iptvChannelFavoriteList.toImmutableList() },
            channelFavoriteListVisibleProvider = { settingsViewModel.iptvChannelFavoriteListVisible },
            onChannelFavoriteListVisibleChange = {
                settingsViewModel.iptvChannelFavoriteListVisible = it
            },
            onClose = { mainContentState.isChannelScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isChannelScreenVisible && settingsViewModel.uiUseClassicPanelScreen },
        onDismissRequest = { mainContentState.isChannelScreenVisible = false },
    ) {
        ClassicChannelScreen(
            channelGroupListProvider = filteredChannelGroupListProvider,
            currentChannelProvider = { mainContentState.currentChannel },
            currentChannelUrlIdxProvider = { mainContentState.currentChannelUrlIdx },
            favoriteChannelListProvider = { mainContentState.favoriteChannelList() },
            showChannelLogoProvider = showChannelLogoProvider,
            onChannelSelected = {
                mainContentState.isChannelScreenVisible = false
                mainContentState.changeCurrentChannel(it)
            },
            onChannelFavoriteToggle = { mainContentState.favoriteChannelOrNot(it) },
            epgListProvider = epgListProvider,
            epgProgrammeReserveListProvider = {
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList)
            },
            showEpgProgrammeProgressProvider = { settingsViewModel.uiShowEpgProgrammeProgress },
            supportPlaybackProvider = { mainContentState.supportPlayback(it, null) },
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            onEpgProgrammePlayback = { channel, programme ->
                mainContentState.isChannelScreenVisible = false
                mainContentState.changeCurrentChannel(channel, null, programme)
            },
            onEpgProgrammeReserve = { channel, programme ->
                mainContentState.reverseEpgProgrammeOrNot(channel, programme)
            },
            videoPlayerMetadataProvider = { videoPlayerState.metadata },
            channelFavoriteEnabledProvider = { settingsViewModel.iptvChannelFavoriteEnable },
            channelFavoriteListVisibleProvider = { settingsViewModel.iptvChannelFavoriteListVisible },
            onChannelFavoriteListVisibleChange = {
                settingsViewModel.iptvChannelFavoriteListVisible = it
            },
            sourceSwitchEnabledProvider = { settingsViewModel.iptvSourceQuickSwitchEnable },
            iptvSourceListProvider = { settingsViewModel.iptvSourceList },
            currentIptvSourceProvider = { settingsViewModel.iptvSourceCurrent },
            onIptvSourceSelected = { iptvSource ->
                if (settingsViewModel.iptvSourceCurrent != iptvSource) {
                    settingsViewModel.iptvSourceCurrent = iptvSource
                    settingsViewModel.iptvLastChannelIdx = 0
                    mainContentState.isChannelScreenVisible = false
                    Snackbar.show("已切换直播源：${iptvSource.name}")
                    // 先清缓存再重载，否则可能把上一个订阅源的缓存内容当成新源的加载出来
                    coroutineScope.launch {
                        IptvRepository(iptvSource).clearCache()
                        mainViewModel.init()
                    }
                }
            },
            onIptvSourceDeleted = { iptvSource ->
                settingsViewModel.iptvSourceList =
                    IptvSourceList(settingsViewModel.iptvSourceList - iptvSource)
            },
            onIptvSourceAdded = { iptvSource ->
                // 手动填写的订阅：存下来并直接切过去用
                settingsViewModel.iptvSourceList =
                    IptvSourceList(settingsViewModel.iptvSourceList + iptvSource)
                settingsViewModel.iptvSourceCurrent = iptvSource
                settingsViewModel.iptvLastChannelIdx = 0
                mainContentState.isChannelScreenVisible = false
                Snackbar.show("已添加并切换直播源：${iptvSource.name}")
                coroutineScope.launch {
                    IptvRepository(iptvSource).clearCache()
                    mainViewModel.init()
                }
            },
            onSystemAppLaunch = { app ->
                // 跳去其它 app 前先把正在播的直播暂停
                videoPlayerState.pause()
                launchApp(context, app.packageName)
            },
            onClose = { mainContentState.isChannelScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isSettingsScreenVisible },
        onDismissRequest = { mainContentState.isSettingsScreenVisible = false },
    ) {
        SettingsScreen(
            channelGroupListProvider = channelGroupListProvider,
            onClose = { mainContentState.isSettingsScreenVisible = false },
        )
    }

    EpgReverseScreen(
        epgProgrammeReserveListProvider = { settingsViewModel.epgChannelReserveList },
        onConfirmReserve = { reserve ->
            filteredChannelGroupListProvider().channelList.firstOrNull { it.name == reserve.channel }
                ?.let {
                    mainContentState.changeCurrentChannel(it)
                }
        },
        onDeleteReserve = { reserve ->
            settingsViewModel.epgChannelReserveList =
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList - reserve)
        },
    )

    UpdateScreen()

    Visible({ settingsViewModel.debugShowFps }) { MonitorScreen() }
}