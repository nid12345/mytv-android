package top.yogiczy.mytv.tv.ui.screens.dlna

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.repositories.dlna.DlnaCastRepository
import top.yogiczy.mytv.tv.ui.material.Drawer
import top.yogiczy.mytv.tv.ui.material.DrawerPosition
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import top.yogiczy.mytv.tv.ui.material.Tag
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme
import top.yogiczy.mytv.tv.ui.tooling.PreviewWithLayoutGrids
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunched
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunchedSaveable
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse

/**
 * 投送到设备
 *
 * 打开时自动搜索局域网内的 DLNA 渲染设备（电视、电视盒子、投影仪等），
 * 选中后把当前正在播放的直播源推送到该设备播放。
 */
@Composable
fun DlnaCastScreen(
    modifier: Modifier = Modifier,
    channelProvider: () -> Channel = { Channel() },
    channelUrlIdxProvider: () -> Int = { 0 },
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current
    val state by DlnaCastRepository.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var isCasting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        DlnaCastRepository.scan(context)
    }

    Drawer(
        position = DrawerPosition.Bottom,
        onDismissRequest = onClose,
        header = { Text("投送到设备") },
    ) {
        LazyColumn(
            modifier = modifier.height(240.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.devices.isEmpty()) {
                item {
                    Text(
                        text = if (state.isScanning) {
                            "正在搜索局域网内的投屏设备…请确认电视已开机并与本机连接同一网络"
                        } else {
                            "未发现投屏设备，请确认电视已开机并与本机连接同一网络"
                        },
                    )
                }
            }

            itemsIndexed(state.devices) { index, device ->
                val isCastingThisDevice = state.castingDeviceId == device.id

                DlnaCastDeviceItem(
                    modifier = Modifier.ifElse(index == 0, Modifier.focusOnLaunchedSaveable()),
                    deviceNameProvider = { device.displayName },
                    deviceInfoProvider = { device.displayInfo },
                    isCastingProvider = { isCastingThisDevice },
                    isBusyProvider = { isCasting },
                    onSelected = {
                        if (isCasting) return@DlnaCastDeviceItem

                        coroutineScope.launch {
                            isCasting = true

                            if (isCastingThisDevice) {
                                // 已投送到该设备，再次选中表示断开
                                withContext(Dispatchers.IO) {
                                    DlnaCastRepository.stop(device)
                                }
                                isCasting = false
                                Snackbar.show("已停止投送")
                            } else {
                                val result = withContext(Dispatchers.IO) {
                                    DlnaCastRepository.cast(
                                        device = device,
                                        channel = channelProvider(),
                                        urlIdx = channelUrlIdxProvider(),
                                    )
                                }
                                isCasting = false

                                result.onSuccess {
                                    onClose()
                                    Snackbar.show("已投送到 ${device.displayName}")
                                }.onFailure {
                                    Snackbar.show(
                                        "投送失败：${it.message ?: "请重试"}",
                                        type = SnackbarType.ERROR,
                                    )
                                }
                            }
                        }
                    },
                )
            }

            item {
                DlnaCastRescanItem(
                    modifier = Modifier.ifElse(
                        state.devices.isEmpty(),
                        Modifier.focusOnLaunched(),
                    ),
                    isScanningProvider = { state.isScanning },
                    onSelect = { DlnaCastRepository.scan(context) },
                )
            }
        }
    }
}

@Composable
private fun DlnaCastDeviceItem(
    modifier: Modifier = Modifier,
    deviceNameProvider: () -> String = { "" },
    deviceInfoProvider: () -> String = { "" },
    isCastingProvider: () -> Boolean = { false },
    isBusyProvider: () -> Boolean = { false },
    onSelected: () -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }
    val isCasting = isCastingProvider()

    ListItem(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .handleKeyEvents(
                onSelect = if (isBusyProvider()) null else onSelected,
            ),
        selected = false,
        onClick = {},
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(deviceNameProvider())

                if (isCasting) Tag("投送中")
            }
        },
        supportingContent = {
            Text(
                deviceInfoProvider().ifBlank { "选中后投送当前频道" },
                maxLines = if (isFocused) Int.MAX_VALUE else 1,
            )
        },
        trailingContent = {
            RadioButton(selected = isCasting, onClick = onSelected)
        },
    )
}

@Composable
private fun DlnaCastRescanItem(
    modifier: Modifier = Modifier,
    isScanningProvider: () -> Boolean = { false },
    onSelect: () -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }

    ListItem(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .handleKeyEvents(
                onSelect = if (isScanningProvider()) null else onSelect,
            ),
        selected = false,
        onClick = {},
        headlineContent = {
            Text(if (isScanningProvider()) "正在搜索…" else "重新搜索")
        },
    )
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun DlnaCastScreenPreview() {
    MyTVTheme {
        PreviewWithLayoutGrids {
            DlnaCastScreen()
        }
    }
}
