package top.yogiczy.mytv.tv.ui.screens.apkmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.WideButton
import top.yogiczy.mytv.core.util.utils.humanizeBytes
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme
import top.yogiczy.mytv.tv.ui.tooling.PreviewWithLayoutGrids
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunched
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.utlis.ApkItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「收到安装包」确认弹窗
 *
 * 推送传完 APK 后弹出，避免「传完了却什么都没发生」。系统未放行「安装未知应用」时，
 * 主按钮变成「去设置」，直接把用户送到对应设置页。
 */
@Composable
fun ApkInstallDialog(
    modifier: Modifier = Modifier,
    apkProvider: () -> ApkItem? = { null },
    errorProvider: () -> String? = { null },
    needPermissionProvider: () -> Boolean = { false },
    onInstall: () -> Unit = {},
    onOpenPermissionSetting: () -> Unit = {},
    onDismissRequest: () -> Unit = {},
) {
    val apk = apkProvider() ?: return
    val error = errorProvider()
    val needPermission = needPermissionProvider()

    val timeText = remember(apk) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(apk.addedAt))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("收到安装包", style = MaterialTheme.typography.titleLarge)

            Text(
                text = apk.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "${apk.size.humanizeBytes()} · $timeText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
            )

            if (needPermission) {
                Text(
                    text = "⚠ 需要先允许本应用安装应用，否则系统会拒绝安装",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.85f),
                )
            }

            if (!error.isNullOrBlank()) {
                Text(
                    text = "⚠ $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.85f),
                )
            }

            Box(Modifier.padding(top = 6.dp)) {
                WideButton(
                    modifier = Modifier
                        .focusOnLaunched()
                        .handleKeyEvents(
                            onSelect = { if (needPermission) onOpenPermissionSetting() else onInstall() }
                        ),
                    onClick = { },
                    title = { Text(if (needPermission) "去放行" else "立即安装") },
                )
            }

            WideButton(
                modifier = Modifier.handleKeyEvents(onSelect = onDismissRequest),
                onClick = { },
                title = { Text("稍后再说") },
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun ApkInstallDialogPreview() {
    MyTVTheme {
        PreviewWithLayoutGrids {
            ApkInstallDialog(
                apkProvider = {
                    ApkItem(name = "MyTV-1.8.apk", path = "", size = 5_400_000, addedAt = 0L)
                },
            )
        }
    }
}
