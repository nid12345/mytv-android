package top.yogiczy.mytv.tv.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroup
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.core.data.utils.ChannelUtil
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import top.yogiczy.mytv.tv.ui.utils.Configs

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /** 线路测速任务，切换直播源时用来取消上一次测速 */
    private var lineSpeedJob: Job? = null

    init {
        init()
    }

    fun init() {
        lineSpeedJob?.cancel()
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading()
            refreshChannel()
            // 线路测速和节目单都慢，并行跑：频道先显示出来，两边各自完成后再刷新界面
            lineSpeedJob = launch { sortChannelLinesBySpeed() }
            refreshEpg()
        }
    }

    /**
     * 后台给各频道的多条线路测速，按播放效果重排
     *
     * 先用源里的原始顺序把频道显示出来，测速完成后再替换成重排结果；
     * 当前正在播放的线路不受影响（重排后会按地址重新定位线路下标）。
     *
     * 这里刻意先等一会儿再开始：刚打开应用时首帧还在加载，
     * 这时要是同时发起一堆探测请求，正在缓冲的画面容易被挤到超时，
     * 表现出来就是「打开后反而播放不了」。等播放稳定下来再慢慢测。
     */
    private suspend fun sortChannelLinesBySpeed() {
        if (!Configs.iptvSourceLineSpeedSortEnable) return

        val ready = _uiState.value as? MainUiState.Ready ?: return
        val source = Configs.iptvSourceCurrent
        // 只有内置默认源参与测速；斗鱼/虎牙/YY 这类每频道单线路的订阅直接跳过
        if (!source.lineSpeedSort) return

        // 让首帧先播起来，也不打扰用户刚打开应用时的操作
        delay(SPEED_SORT_START_DELAY)

        runCatching {
            // 正在播的那个频道的线路显然可用，不必再测（用上次记住的频道序号定位）
            val playingChannelUrlList = ready.channelGroupList.channelList
                .getOrNull(Configs.iptvLastChannelIdx)?.urlList?.toSet() ?: emptySet()

            // 「潮汕节目回放」是点播回放地址（mp4），不参与线路测速
            val baseGroups = ready.channelGroupList
                .filter { it.name != Constants.CHAOSHAN_REPLAY_SOURCE.name }
            val extraGroups = ready.channelGroupList
                .filter { it.name == Constants.CHAOSHAN_REPLAY_SOURCE.name }

            val sortedBase = IptvRepository(source)
                .sortChannelLinesBySpeed(
                    ChannelGroupList(baseGroups),
                    excludeUrls = playingChannelUrlList,
                )

            val latest = _uiState.value as? MainUiState.Ready ?: return@runCatching
            val merged = ChannelGroupList(sortedBase + extraGroups)
            if (merged == latest.channelGroupList) return@runCatching

            _uiState.value = latest.copy(channelGroupList = merged)
        }.onFailure {
            // 测速失败不影响正常使用，保持源里原有顺序
        }
    }

    companion object {
        /** 打开应用后等待多久再开始测速，先让首帧播起来 */
        private const val SPEED_SORT_START_DELAY = 15_000L

        /** 潮汕节目回放加载超时，超时就算了，不拖累默认源显示 */
        private const val CHAOSHAN_LOAD_TIMEOUT = 8_000L
    }

    private suspend fun refreshChannel() {
        flow {
            coroutineScope {
                // 内置「潮汕节目回放」与主源并行拉取（带超时），主源先到先显示
                val chaoshanDeferred = if (shouldAppendChaoshan()) async { loadChaoshanGroup() }
                else null

                val base = IptvRepository(Configs.iptvSourceCurrent)
                    .getChannelGroupList(cacheTime = Configs.iptvSourceCacheTime)
                    .also { cacheSourceGroupNames(it) }

                emit(base)

                val chaoshan = chaoshanDeferred?.await() ?: return@coroutineScope
                emit(ChannelGroupList(base + chaoshan))
            }
        }
            .retryWhen { _, attempt ->
                if (attempt >= Constants.HTTP_RETRY_COUNT) return@retryWhen false

                _uiState.value =
                    MainUiState.Loading("获取远程直播源(${attempt + 1}/${Constants.HTTP_RETRY_COUNT})...")
                delay(Constants.HTTP_RETRY_INTERVAL)
                true
            }
            .catch {
                _uiState.value = MainUiState.Error(it.message)
            }
            .map { hybridChannel(it) }
            .map {
                _uiState.value = MainUiState.Ready(channelGroupList = it)
                it
            }
            .collect()
    }

    /** 潮汕节目回放只在浏览默认直播源时附加显示 */
    private fun shouldAppendChaoshan(): Boolean {
        if (!Configs.iptvChaoshanSourceEnable) return false
        val current = Configs.iptvSourceCurrent
        return Constants.normalizeIptvSource(current).url == Constants.IPTV_SOURCE_LIST.first().url
    }

    /**
     * 拉取内置「潮汕节目回放」，把它的各栏目压平成一个分组
     *
     * 加载失败（断网、链接失效）返回 null，静默跳过，不影响默认源正常显示。
     */
    private suspend fun loadChaoshanGroup(): ChannelGroup? = runCatching {
        withTimeout(CHAOSHAN_LOAD_TIMEOUT) {
            IptvRepository(Constants.CHAOSHAN_REPLAY_SOURCE)
                .getChannelGroupList(cacheTime = Configs.iptvSourceCacheTime)
        }
    }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { replay ->
        ChannelGroup(
            name = Constants.CHAOSHAN_REPLAY_SOURCE.name,
            channelList = ChannelList(replay.flatMap { it.channelList }),
        )
    }

    /** 缓存订阅源出现过的分组名，供「全部分组管理」跨源查看与显隐 */
    private fun cacheSourceGroupNames(groupList: ChannelGroupList) {
        val url = Constants.normalizeIptvSource(Configs.iptvSourceCurrent).url
        Configs.iptvSourceGroupNamesMap =
            Configs.iptvSourceGroupNamesMap + (url to groupList.map { it.name })
    }

    private suspend fun hybridChannel(channelGroupList: ChannelGroupList) =
        withContext(Dispatchers.Default) {
            val hybridMode = Configs.iptvHybridMode
            return@withContext when (hybridMode) {
                Configs.IptvHybridMode.DISABLE -> channelGroupList
                Configs.IptvHybridMode.IPTV_FIRST -> {
                    ChannelGroupList(channelGroupList.map { group ->
                        group.copy(channelList = ChannelList(group.channelList.map { channel ->
                            channel.copy(
                                urlList = channel.urlList.plus(
                                    ChannelUtil.getHybridWebViewUrl(channel.name) ?: emptyList()
                                )
                            )
                        }))
                    })
                }

                Configs.IptvHybridMode.HYBRID_FIRST -> {
                    ChannelGroupList(channelGroupList.map { group ->
                        group.copy(channelList = ChannelList(group.channelList.map { channel ->
                            channel.copy(
                                urlList = (ChannelUtil.getHybridWebViewUrl(channel.name)
                                    ?: emptyList())
                                    .plus(channel.urlList)
                            )
                        }))
                    })
                }
            }
        }

    private suspend fun refreshEpg() {
        if (!Configs.epgEnable) return

        if (_uiState.value is MainUiState.Ready) {
            EpgList.clearCache()
            val channelGroupList = (_uiState.value as MainUiState.Ready).channelGroupList

            flow {
                emit(
                    EpgRepository(
                        source = Configs.epgSourceCurrent,
                        // 其余节目单来源作为补齐源：主源缺哪个频道，就从这里补哪个频道
                        fallbackSources = Constants.EPG_SOURCE_LIST,
                    ).getEpgList(
                        filteredChannels = channelGroupList.channelList.map { it.epgName },
                        refreshTimeThreshold = Configs.epgRefreshTimeThreshold,
                    )
                )
            }
                .retry(Constants.HTTP_RETRY_COUNT) { delay(Constants.HTTP_RETRY_INTERVAL); true }
                .catch {
                    emit(EpgList())
                    Snackbar.show("节目单获取失败，请检查网络连接", type = SnackbarType.ERROR)
                }
                .map { epgList ->
                    _uiState.value = (_uiState.value as MainUiState.Ready).copy(epgList = epgList)
                }
                .collect()
        }
    }
}

sealed interface MainUiState {
    data class Loading(val message: String? = null) : MainUiState
    data class Error(val message: String? = null) : MainUiState
    data class Ready(
        val channelGroupList: ChannelGroupList = ChannelGroupList(),
        val epgList: EpgList = EpgList(),
    ) : MainUiState
}