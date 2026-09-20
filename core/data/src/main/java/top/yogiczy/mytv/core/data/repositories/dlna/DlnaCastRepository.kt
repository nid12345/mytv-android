package top.yogiczy.mytv.core.data.repositories.dlna

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.dlna.DlnaDevice
import top.yogiczy.mytv.core.data.utils.Loggable

/**
 * DLNA 投送状态仓库
 *
 * 维护局域网内的可投送设备列表，以及当前正在投送的设备，
 * 供播放界面选择设备并投送当前正在播放的直播源。
 */
object DlnaCastRepository : Loggable() {
    /**
     * 搜索持续时间
     */
    private const val SCAN_TIMEOUT_MS = 6000L

    data class State(
        /**
         * 已发现的设备
         */
        val devices: List<DlnaDevice> = emptyList(),

        /**
         * 是否正在搜索
         */
        val isScanning: Boolean = false,

        /**
         * 正在投送的设备标识
         */
        val castingDeviceId: String = "",
    ) {
        val castingDevice: DlnaDevice?
            get() = devices.firstOrNull { it.id == castingDeviceId }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var scanJob: Job? = null
    private var scanSequence = 0

    /**
     * 搜索局域网内的投屏设备
     */
    fun scan(context: Context?) {
        scanJob?.cancel()

        val sequence = ++scanSequence

        scanJob = scope.launch {
            _state.update { it.copy(isScanning = true, devices = emptyList()) }

            try {
                DlnaDiscovery.discover(context = context, timeoutMs = SCAN_TIMEOUT_MS) { device ->
                    _state.update { state ->
                        if (state.devices.any { it.id == device.id }) {
                            state
                        } else {
                            state.copy(
                                devices = (state.devices + device).sortedBy { it.displayName }
                            )
                        }
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                log.e("搜索投屏设备失败: ${ex.message}")
            } finally {
                if (sequence == scanSequence) {
                    _state.update { it.copy(isScanning = false) }
                }
            }
        }
    }

    /**
     * 把当前频道投送到设备播放
     */
    suspend fun cast(
        device: DlnaDevice,
        channel: Channel,
        urlIdx: Int,
    ): Result<Unit> {
        val idx = if (channel.urlList.isEmpty()) 0 else urlIdx.coerceIn(0, channel.urlList.size - 1)
        val url = channel.urlList.getOrNull(idx).orEmpty()

        val result = DlnaController.cast(device, url, channel.name)

        if (result.isSuccess) {
            val previous = _state.value.castingDevice
            if (previous != null && previous.id != device.id) {
                // 切换设备时停止上一台，避免两台设备同时播放
                runCatching { DlnaController.stop(previous) }
            }

            _state.update { it.copy(castingDeviceId = device.id) }
        }

        return result
    }

    /**
     * 停止指定设备的投送
     */
    suspend fun stop(device: DlnaDevice): Boolean {
        val succeeded = DlnaController.stop(device)

        _state.update {
            if (it.castingDeviceId == device.id) it.copy(castingDeviceId = "") else it
        }

        return succeeded
    }
}
