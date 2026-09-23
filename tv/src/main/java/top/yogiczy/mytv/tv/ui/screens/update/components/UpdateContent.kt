package top.yogiczy.mytv.tv.ui.screens.update.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.WideButton
import top.yogiczy.mytv.core.data.entities.git.GitRelease
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme
import top.yogiczy.mytv.tv.ui.tooling.PreviewWithLayoutGrids
import top.yogiczy.mytv.tv.ui.utils.customBackground
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunched
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

@Composable
fun UpdateContent(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit = {},
    releaseProvider: () -> GitRelease = { GitRelease() },
    currentVersionProvider: () -> String = { "" },
    isUpdateAvailableProvider: () -> Boolean = { false },
    isCheckingProvider: () -> Boolean = { false },
    isUpdatingProvider: () -> Boolean = { false },
    downloadProgressProvider: () -> Int = { 0 },
    onUpdateAndInstall: () -> Unit = {},
) {
    val release = releaseProvider()
    val isUpdateAvailable = isUpdateAvailableProvider()
    val isChecking = isCheckingProvider()
    val isUpdating = isUpdatingProvider()
    val downloadProgress = downloadProgressProvider()

    Row(
        modifier = modifier
            .fillMaxSize()
            .customBackground()
            .padding(horizontal = 130.dp, vertical = 88.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.width(360.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("应用更新", style = MaterialTheme.typography.headlineMedium)

            Text(
                "当前版本: v${currentVersionProvider()}",
                style = MaterialTheme.typography.bodyLarge,
            )

            Text(
                text = when {
                    isUpdating -> "正在下载新版本…"
                    isChecking -> "正在检查更新…"
                    else -> "最新版本: v${release.version}"
                },
                style = MaterialTheme.typography.bodyLarge,
            )

            LazyColumn {
                item {
                    Text(release.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                isUpdating -> {
                    WideButton(
                        modifier = Modifier
                            .focusOnLaunched()
                            .handleKeyEvents(onSelect = { }),
                        onClick = { },
                        title = { Text("正在下载 $downloadProgress%") },
                    )

                    WideButton(
                        modifier = Modifier.handleKeyEvents(onSelect = onDismissRequest),
                        onClick = { },
                        title = { Text("后台下载") },
                    )
                }

                isUpdateAvailable -> {
                    WideButton(
                        modifier = Modifier
                            .focusOnLaunched()
                            .handleKeyEvents(onSelect = onUpdateAndInstall),
                        onClick = { },
                        title = { Text("立即更新") },
                    )

                    WideButton(
                        modifier = Modifier.handleKeyEvents(onSelect = onDismissRequest),
                        onClick = { },
                        title = { Text("忽略") },
                    )
                }

                else -> {
                    WideButton(
                        modifier = Modifier
                            .focusOnLaunched()
                            .handleKeyEvents(onSelect = onDismissRequest),
                        onClick = { },
                        title = { Text(if (isChecking) "正在检查…" else "当前为最新版本") },
                    )
                }
            }
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun UpdateDialogPreview() {
    MyTVTheme {
        PreviewWithLayoutGrids {
            UpdateContent(
                releaseProvider = {
                    GitRelease(
                        version = "1.7",
                        downloadUrl = "",
                        description = "更新日志".repeat(100),
                    )
                },
                currentVersionProvider = { "1.6" },
                isUpdateAvailableProvider = { true },
            )
        }
    }
}
