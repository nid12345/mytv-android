package top.yogiczy.mytv.tv.ui.screens.components

import androidx.annotation.IntRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.tv.ui.utils.Configs

@Stable
class ScreenAutoClose internal constructor(
    @IntRange(from = 0) private val timeout: Long,
    private val onTimeout: () -> Unit = {},
) {
    fun active() {
        channel.trySend(timeout)
    }

    private val channel = Channel<Long>(Channel.CONFLATED)

    @OptIn(FlowPreview::class)
    suspend fun observe() {
        channel.consumeAsFlow().debounce { it }.collect {
            onTimeout()
        }
    }
}

@Composable
fun rememberScreenAutoCloseState(
    // 接通设置里的「自动关闭界面延时」：原实现写死 15 秒常量，设置项形同虚设
    @IntRange(from = 0) timeout: Long = Configs.uiScreenAutoCloseDelay,
    onTimeout: () -> Unit = {},
) = remember { ScreenAutoClose(timeout = timeout, onTimeout = onTimeout) }.also {
    LaunchedEffect(it) { it.observe() }
    it.active()
}