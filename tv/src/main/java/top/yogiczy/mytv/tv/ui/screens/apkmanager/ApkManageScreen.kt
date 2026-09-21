package top.yogiczy.mytv.tv.ui.screens.apkmanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.util.utils.ApkInstaller
import top.yogiczy.mytv.core.util.utils.humanizeBytes
import top.yogiczy.mytv.tv.ui.material.Drawer
import top.yogiczy.mytv.tv.ui.material.DrawerPosition
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import top.yogiczy.mytv.tv.ui.screens.applauncher.AppIcon
import top.yogiczy.mytv.tv.ui.screens.applauncher.queryLaunchableApps
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunchedSaveable
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse
import top.yogiczy.mytv.tv.utlis.ApkItem
import top.yogiczy.mytv.tv.utlis.ApkPackageManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 安装包管理
 *
 * - 收到的安装包：安装（确认键）/ 删除（长按确认键）
 * - 已安装应用：确认键调起系统卸载
 */
@Composable
fun ApkManageScreen(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current
    var apkList by remember { mutableStateOf(ApkPackageManager.list(context)) }
    val installedApps = remember { queryLaunchableApps(context) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Drawer(
        position = DrawerPosition.Bottom,
        onDismissRequest = onClose,
        header = { Text("安装包管理") },
    ) {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Text(
                    text = "收到的安装包（确认键：安装；长按确认键：删除）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp, start = 8.dp),
                )
            }

            // 系统没放行「安装未知应用」时，先给一个一键跳设置的入口——
            // 这种情况下调起安装会被系统直接拒绝
            if (!ApkInstaller.canRequestPackageInstalls(context)) {
                item {
                    ListItem(
                        modifier = Modifier
                            .ifElse(apkList.isEmpty(), Modifier.focusOnLaunchedSaveable())
                            .handleKeyEvents(onSelect = {
                                if (ApkInstaller.openUnknownSourceSetting(context)) {
                                    Snackbar.show("请在系统设置里允许 MyTV 安装应用")
                                } else {
                                    Snackbar.show(
                                        "当前系统没有该设置页，请到系统设置里手动放行",
                                        type = SnackbarType.ERROR,
                                    )
                                }
                            }),
                        colors = ListItemDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        selected = false,
                        headlineContent = {
                            Text(
                                text = "⚠ 允许本应用安装应用（当前未放行）",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = "确认键跳到系统设置页",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        onClick = {},
                    )
                }
            }

            if (apkList.isEmpty()) {
                item {
                    Text(
                        text = "（暂无，可在「推送」页扫码上传 APK）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            itemsIndexed(apkList) { index, apk ->
                ApkItemRow(
                    modifier = Modifier.ifElse(index == 0, Modifier.focusOnLaunchedSaveable()),
                    apk = apk,
                    dateText = dateFormat.format(Date(apk.addedAt)),
                    onInstall = {
                        val error = ApkInstaller.installApk(context, apk.path)
                        if (error == null) {
                            Snackbar.show("正在调起安装：${apk.name}")
                        } else {
                            Snackbar.show("调起安装失败：$error", type = SnackbarType.ERROR)
                        }
                    },
                    onDelete = {
                        ApkPackageManager.delete(apk)
                        apkList = ApkPackageManager.list(context)
                        Snackbar.show("已删除安装包：${apk.name}")
                    },
                )
            }

            item {
                Text(
                    text = "已安装应用（确认键：卸载）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp, start = 8.dp),
                )
            }

            itemsIndexed(installedApps) { _, app ->
                ListItem(
                    modifier = Modifier
                        .handleKeyEvents(onSelect = {
                            // 调起系统卸载确认
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_DELETE,
                                        Uri.parse("package:${app.packageName}"),
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }.onFailure {
                                Snackbar.show("无法卸载该应用", type = SnackbarType.ERROR)
                            }
                        }),
                    colors = ListItemDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    selected = false,
                    leadingContent = { AppIcon(drawable = app.icon) },
                    headlineContent = {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(),
                        )
                    },
                    supportingContent = { Text(app.packageName, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun ApkItemRow(
    modifier: Modifier = Modifier,
    apk: ApkItem,
    dateText: String,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    ListItem(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .handleKeyEvents(
                onSelect = onInstall,
                onLongSelect = onDelete,
            ),
        colors = ListItemDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
        ),
        selected = false,
        headlineContent = {
            Text(
                text = apk.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.ifElse(isFocused, Modifier.basicMarquee()),
            )
        },
        supportingContent = {
            Text(
                text = "${apk.size.humanizeBytes()} · $dateText",
                style = MaterialTheme.typography.labelSmall,
            )
        },
        trailingContent = {
            if (isFocused) {
                Text(
                    text = "确认安装 / 长按删除",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                )
            }
        },
        onClick = {},
    )
}
