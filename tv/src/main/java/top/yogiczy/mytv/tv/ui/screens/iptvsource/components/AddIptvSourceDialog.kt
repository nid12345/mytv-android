package top.yogiczy.mytv.tv.ui.screens.iptvsource.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.screens.components.Qrcode
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.saveRequestFocus
import top.yogiczy.mytv.tv.utlis.HttpServer

/**
 * 添加自定义直播源
 *
 * 以前只能靠手机扫码推送，这里补一个能直接填的数据框：
 * 起个名字 + 填订阅链接，保存即可使用。扫码入口保留在右侧。
 */
@Composable
fun AddIptvSourceDialog(
    modifier: Modifier = Modifier,
    onConfirm: (name: String, url: String) -> Unit = { _, _ -> },
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val serverUrl = HttpServer.serverUrl

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    val nameFocusRequester = remember { FocusRequester() }
    val urlFocusRequester = remember { FocusRequester() }
    val confirmFocusRequester = remember { FocusRequester() }

    fun save() {
        val trimmedUrl = url.trim()
        val trimmedName = name.trim().ifBlank { "自定义直播源" }

        when {
            trimmedUrl.isBlank() -> error = "请填写订阅链接"
            !trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://") ->
                error = "订阅链接需以 http:// 或 https:// 开头"

            else -> {
                error = ""
                onConfirm(trimmedName, trimmedUrl)
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(880.dp)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "添加自定义直播源",
                style = MaterialTheme.typography.titleMedium,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // 左边：直接填写名称与链接
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    InputRow(
                        label = "名称",
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "自定义直播源",
                        focusRequester = nameFocusRequester,
                        focusOnLaunched = true,
                        onTap = { keyboard?.show() },
                    )

                    InputRow(
                        label = "链接",
                        value = url,
                        onValueChange = { url = it },
                        placeholder = "https://example.com/live.m3u",
                        focusRequester = urlFocusRequester,
                        keyboardType = KeyboardType.Uri,
                        onTap = { keyboard?.show() },
                    )

                    if (error.isNotBlank()) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            modifier = Modifier
                                .focusRequester(confirmFocusRequester)
                                .handleKeyEvents(onSelect = { save() }),
                            onClick = { save() },
                            shape = ButtonDefaults.shape(shape = MaterialTheme.shapes.medium),
                        ) {
                            Text("保存并使用")
                        }

                        Text(
                            text = "也可以按「返回」取消",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 右边：手机上填更省事，保留扫码入口
                Column(
                    modifier = Modifier.width(220.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "或扫码用手机添加",
                        style = MaterialTheme.typography.labelMedium,
                    )

                    Qrcode(
                        modifier = Modifier
                            .width(180.dp)
                            .height(180.dp),
                        textProvider = { serverUrl },
                    )

                    Text(
                        text = serverUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 一行输入框
 *
 * 电视上没法直接打字，聚焦时会把输入法唤起来；
 * 遥控器按「确定」或屏幕上点一下都能唤起。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun InputRow(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    focusRequester: FocusRequester = remember { FocusRequester() },
    focusOnLaunched: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onTap: () -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }

    if (focusOnLaunched) {
        LaunchedEffect(Unit) { focusRequester.saveRequestFocus() }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(56.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                    MaterialTheme.shapes.small,
                )
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.border,
                    shape = MaterialTheme.shapes.small,
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { isFocused = it.isFocused }
                    // 消费掉点击，避免冒泡到弹层把它关掉
                    .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
                    .onPreviewKeyEvent { event ->
                        // 遥控器/键盘按「确定」时把输入法唤起来，其余按键交给输入框自己处理
                        if (event.type == KeyEventType.KeyUp &&
                            (event.key == Key.DirectionCenter || event.key == Key.Enter ||
                                    event.key == Key.NumPadEnter)
                        ) {
                            onTap()
                            true
                        } else false
                    },
            )
        }
    }
}
