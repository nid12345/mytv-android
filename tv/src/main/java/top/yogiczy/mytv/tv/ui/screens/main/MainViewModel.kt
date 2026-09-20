package top.yogiczy.mytv.tv.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
     */
    private suspend fun sortChannelLinesBySpeed() {
        val ready = _uiState.value as? MainUiState.Ready ?: return

        runCatching {
            val sorted = IptvRepository(Configs.iptvSourceCurrent)
                .sortChannelLinesBySpeed(ready.channelGroupList)

            val latest = _uiState.value as? MainUiState.Ready ?: return@runCatching
            if (sorted.size != latest.channelGroupList.size) return@runCatching
            if (sorted == latest.channelGroupList) return@runCatching

            _uiState.value = latest.copy(channelGroupList = sorted)
        }.onFailure {
            // 测速失败不影响正常使用，保持源里原有顺序
        }
    }

    private suspend fun refreshChannel() {
        flow {
            emit(
                IptvRepository(Configs.iptvSourceCurrent).getChannelGroupList(cacheTime = Configs.iptvSourceCacheTime)
            )
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