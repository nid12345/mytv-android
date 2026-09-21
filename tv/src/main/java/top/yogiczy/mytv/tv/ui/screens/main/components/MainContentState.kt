package top.yogiczy.mytv.tv.ui.screens.main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelIdx
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserve
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserveList
import top.yogiczy.mytv.core.data.utils.ChannelUtil
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Loggable
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.screens.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screens.videoplayer.VideoPlayerState
import top.yogiczy.mytv.tv.ui.screens.videoplayer.rememberVideoPlayerState
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Stable
class MainContentState(
    private val coroutineScope: CoroutineScope,
    private val videoPlayerState: VideoPlayerState,
    private val channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    private val settingsViewModel: SettingsViewModel,
) : Loggable() {
    private var _currentChannel by mutableStateOf(Channel())
    val currentChannel get() = _currentChannel

    private var _currentChannelUrlIdx by mutableIntStateOf(0)
    val currentChannelUrlIdx get() = _currentChannelUrlIdx

    private var _currentPlaybackEpgProgramme by mutableStateOf<EpgProgramme?>(null)
    val currentPlaybackEpgProgramme get() = _currentPlaybackEpgProgramme

    private var _isTempChannelScreenVisible by mutableStateOf(false)
    var isTempChannelScreenVisible
        get() = _isTempChannelScreenVisible
        set(value) {
            _isTempChannelScreenVisible = value
        }

    private var _isChannelScreenVisible by mutableStateOf(false)
    var isChannelScreenVisible
        get() = _isChannelScreenVisible
        set(value) {
            _isChannelScreenVisible = value
        }

    private var _isSettingsScreenVisible by mutableStateOf(false)
    var isSettingsScreenVisible
        get() = _isSettingsScreenVisible
        set(value) {
            _isSettingsScreenVisible = value
        }

    private var _isVideoPlayerControllerScreenVisible by mutableStateOf(false)
    var isVideoPlayerControllerScreenVisible
        get() = _isVideoPlayerControllerScreenVisible
        set(value) {
            _isVideoPlayerControllerScreenVisible = value
        }

    private var _isQuickOpScreenVisible by mutableStateOf(false)
    var isQuickOpScreenVisible
        get() = _isQuickOpScreenVisible
        set(value) {
            _isQuickOpScreenVisible = value
        }

    private var _isEpgScreenVisible by mutableStateOf(false)
    var isEpgScreenVisible
        get() = _isEpgScreenVisible
        set(value) {
            _isEpgScreenVisible = value
        }

    private var _isChannelUrlScreenVisible by mutableStateOf(false)
    var isChannelUrlScreenVisible
        get() = _isChannelUrlScreenVisible
        set(value) {
            _isChannelUrlScreenVisible = value
        }

    private var _isDlnaCastScreenVisible by mutableStateOf(false)
    var isDlnaCastScreenVisible
        get() = _isDlnaCastScreenVisible
        set(value) {
            _isDlnaCastScreenVisible = value
        }

    private var _isVideoPlayerDisplayModeScreenVisible by mutableStateOf(false)
    var isVideoPlayerDisplayModeScreenVisible
        get() = _isVideoPlayerDisplayModeScreenVisible
        set(value) {
            _isVideoPlayerDisplayModeScreenVisible = value
        }

    init {
        val channelGroupList = channelGroupListProvider()

        // 启动频道：默认接着上次退出时的频道；关掉「记住上次频道」则从第一个开始
        val launchChannelIdx =
            if (settingsViewModel.iptvLastChannelRememberEnable)
                settingsViewModel.iptvLastChannelIdx
            else 0

        changeCurrentChannel(channelGroupList.channelList.getOrElse(launchChannelIdx) {
            channelGroupList.channelList.firstOrNull() ?: Channel()
        })

        videoPlayerState.onReady {
            settingsViewModel.iptvPlayableHostList += getUrlHost(_currentChannel.urlList[_currentChannelUrlIdx])
            coroutineScope.launch {
                val name = _currentChannel.name
                val urlIdx = _currentChannelUrlIdx
                delay(Constants.UI_TEMP_CHANNEL_SCREEN_SHOW_DURATION)
                if (name == _currentChannel.name && urlIdx == _currentChannelUrlIdx) {
                    _isTempChannelScreenVisible = false
                }
            }
        }

        videoPlayerState.onError {
            if (_currentPlaybackEpgProgramme != null) return@onError

            settingsViewModel.iptvPlayableHostList -= getUrlHost(_currentChannel.urlList[_currentChannelUrlIdx])

            if (_currentChannelUrlIdx < _currentChannel.urlList.size - 1) {
                changeCurrentChannel(_currentChannel, _currentChannelUrlIdx + 1)
            }
        }

        videoPlayerState.onInterrupt {
            changeCurrentChannel(
                _currentChannel,
                _currentChannelUrlIdx,
                _currentPlaybackEpgProgramme
            )
        }
    }

    /**
     * 收藏频道列表（跨订阅源）
     *
     * 当前订阅源里存在的频道用源里的最新数据（线路最新、还能享受测速排序）；
     * 收藏自其它订阅源、当前源里没有的频道，用收藏时保存的快照播放。
     */
    fun favoriteChannelList(): ChannelList =
        settingsViewModel.resolveFavoriteChannelList(channelGroupListProvider().channelList)

    private fun getPrevFavoriteChannel(): Channel? {
        if (!settingsViewModel.iptvChannelFavoriteListVisible) return null

        val favorites = favoriteChannelList()

        return if (_currentChannel in favorites && _currentChannel != favorites.first()) {
            val currentIdx = favorites.indexOf(_currentChannel)
            favorites[currentIdx - 1]
        } else if (settingsViewModel.iptvChannelFavoriteChangeBoundaryJumpOut) {
            settingsViewModel.iptvChannelFavoriteListVisible = false
            channelGroupListProvider().channelList.lastOrNull()
        } else {
            favorites.lastOrNull()
        }

    }

    private fun getNextFavoriteChannel(): Channel? {
        if (!settingsViewModel.iptvChannelFavoriteListVisible) return null

        val favorites = favoriteChannelList()

        return if (_currentChannel in favorites && _currentChannel != favorites.last()) {
            val currentIdx = favorites.indexOf(_currentChannel)
            favorites[currentIdx + 1]
        } else if (settingsViewModel.iptvChannelFavoriteChangeBoundaryJumpOut) {
            settingsViewModel.iptvChannelFavoriteListVisible = false
            channelGroupListProvider().channelList.firstOrNull()
        } else {
            favorites.firstOrNull()
        }
    }

    private fun getPrevChannel(): Channel {
        return getPrevFavoriteChannel() ?: run {
            val channelGroupList = channelGroupListProvider()
            val currentIdx = channelGroupList.channelIdx(_currentChannel)
            return channelGroupList.channelList.getOrElse(currentIdx - 1) {
                channelGroupList.channelList.lastOrNull() ?: Channel()
            }
        }
    }

    private fun getNextChannel(): Channel {
        return getNextFavoriteChannel() ?: run {
            val channelGroupList = channelGroupListProvider()
            val currentIdx = channelGroupList.channelIdx(_currentChannel)
            return channelGroupList.channelList.getOrElse(currentIdx + 1) {
                channelGroupList.channelList.firstOrNull() ?: Channel()
            }
        }
    }

    private fun getUrlIdx(urlList: List<String>, urlIdx: Int? = null): Int {
        val idx = if (urlIdx == null) urlList.indexOfFirst {
            settingsViewModel.iptvPlayableHostList.contains(getUrlHost(it))
        }
        else (urlIdx + urlList.size) % urlList.size

        return max(0, min(idx, urlList.size - 1))
    }

    fun changeCurrentChannel(
        channel: Channel,
        urlIdx: Int? = null,
        playbackEpgProgramme: EpgProgramme? = null,
    ) {
        if (channel == _currentChannel && urlIdx == _currentChannelUrlIdx && playbackEpgProgramme == _currentPlaybackEpgProgramme) return

        if (channel == _currentChannel && urlIdx != _currentChannelUrlIdx) {
            settingsViewModel.iptvPlayableHostList -= getUrlHost(_currentChannel.urlList[_currentChannelUrlIdx])
        }

        _isTempChannelScreenVisible = true

        _currentChannel = channel
        settingsViewModel.iptvLastChannelIdx =
            channelGroupListProvider().channelIdx(_currentChannel)

        _currentChannelUrlIdx = getUrlIdx(_currentChannel.urlList, urlIdx)

        _currentPlaybackEpgProgramme = playbackEpgProgramme

        var url = _currentChannel.urlList[_currentChannelUrlIdx]
        if (_currentPlaybackEpgProgramme != null) {
            val timeFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
            val query = listOf(
                "playseek=",
                timeFormat.format(_currentPlaybackEpgProgramme!!.startAt),
                "-",
                timeFormat.format(_currentPlaybackEpgProgramme!!.endAt),
            ).joinToString("")
            url = if (URI(url).query.isNullOrBlank()) "$url?$query" else "$url&$query"
            url = ChannelUtil.urlToCanPlayback(url)
        }

        log.d("播放${_currentChannel.name}（${_currentChannelUrlIdx + 1}/${_currentChannel.urlList.size}）: $url")

        if (ChannelUtil.isHybridWebViewUrl(url)) {
            videoPlayerState.stop()
        } else {
            videoPlayerState.prepare(url)
        }
    }

    /**
     * 频道列表被重建（例如线路按测速结果重排）后，把当前频道重新绑定到新对象上
     *
     * 频道是按值比较的，列表重建后旧对象就找不到了，换台会跳到错误的位置；
     * 这里按频道名重新绑定，并把线路下标重新定位到正在播放的那条线路上。
     */
    fun onChannelGroupListChanged() {
        val newChannel = channelGroupListProvider().channelList
            .firstOrNull { it.name == _currentChannel.name } ?: return
        if (newChannel == _currentChannel) return

        val playingUrl = _currentChannel.urlList.getOrNull(_currentChannelUrlIdx)

        _currentChannel = newChannel

        if (playingUrl != null) {
            val idx = newChannel.urlList.indexOf(playingUrl)
            if (idx >= 0) _currentChannelUrlIdx = idx
        }
    }

    fun changeCurrentChannelToPrev() {
        changeCurrentChannel(getPrevChannel())
    }

    fun changeCurrentChannelToNext() {
        changeCurrentChannel(getNextChannel())
    }

    fun favoriteChannelOrNot(channel: Channel) {
        if (!settingsViewModel.iptvChannelFavoriteEnable) return

        if (settingsViewModel.iptvChannelFavoriteList.contains(channel.name)) {
            settingsViewModel.toggleFavoriteChannel(channel)
            Snackbar.show("取消收藏：${channel.name}")
        } else {
            settingsViewModel.toggleFavoriteChannel(channel)
            Snackbar.show("已收藏：${channel.name}")
        }
    }

    fun reverseEpgProgrammeOrNot(channel: Channel, programme: EpgProgramme) {
        val reverse = settingsViewModel.epgChannelReserveList.firstOrNull {
            it.test(channel, programme)
        }

        if (reverse != null) {
            settingsViewModel.epgChannelReserveList =
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList - reverse)
            Snackbar.show("取消预约：${reverse.channel} - ${reverse.programme}")
        } else {
            val newReserve = EpgProgrammeReserve(
                channel = channel.name,
                programme = programme.title,
                startAt = programme.startAt,
                endAt = programme.endAt,
            )

            settingsViewModel.epgChannelReserveList =
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList + newReserve)
            Snackbar.show("已预约：${channel.name} - ${programme.title}")
        }
    }

    fun supportPlayback(
        channel: Channel = _currentChannel,
        urlIdx: Int? = _currentChannelUrlIdx,
    ): Boolean {
        val currentUrlIdx = getUrlIdx(channel.urlList, urlIdx)
        return ChannelUtil.urlSupportPlayback(channel.urlList[currentUrlIdx])
    }
}

@Composable
fun rememberMainContentState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    videoPlayerState: VideoPlayerState = rememberVideoPlayerState(),
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    settingsViewModel: SettingsViewModel = viewModel(),
) = remember {
    MainContentState(
        coroutineScope = coroutineScope,
        videoPlayerState = videoPlayerState,
        channelGroupListProvider = channelGroupListProvider,
        settingsViewModel = settingsViewModel,
    )
}

private fun getUrlHost(url: String): String {
    return url.split("://").getOrElse(1) { "" }.split("/").firstOrNull() ?: url
}