package top.yogiczy.mytv.tv.ui.screens.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Switch
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.utils.SP
import top.yogiczy.mytv.tv.ui.material.LocalPopupManager
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.screens.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screens.update.UpdateViewModel

@Composable
fun SettingsCategoryApp(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = viewModel(),
    updateViewModel: UpdateViewModel = viewModel(),
) {
    SettingsContentList(modifier) {
        item {
            SettingsListItem(
                modifier = Modifier.focusRequester(it),
                headlineContent = "开机自启",
                supportingContent = "请确保当前设备支持该功能",
                trailingContent = {
                    Switch(settingsViewModel.appBootLaunch, null)
                },
                onSelected = {
                    settingsViewModel.appBootLaunch = !settingsViewModel.appBootLaunch
                },
            )
        }

        item {
            val popupManager = LocalPopupManager.current
            val focusRequester = remember { FocusRequester() }
            val context = LocalContext.current
            val currentVersion = remember {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull() ?: ""
            }

            SettingsListItem(
                modifier = Modifier.focusRequester(focusRequester),
                headlineContent = "应用更新",
                supportingContent = when {
                    updateViewModel.isChecking -> "正在检查更新… 当前版本 v$currentVersion"
                    updateViewModel.hasChecked ->
                        "当前版本 v$currentVersion · 最新 v${updateViewModel.latestRelease.version}"
                    else -> "当前版本 v$currentVersion · 点按检查是否有新版本"
                },
                trailingContent = if (updateViewModel.isUpdateAvailable) "发现新版本" else "检查",
                onSelected = {
                    popupManager.push(focusRequester, true)
                    // 计数 +1 让更新面板强制重查一次
                    updateViewModel.manualCheckRequest += 1
                    updateViewModel.visible = true
                },
            )
        }

        item {
            val coroutineScope = rememberCoroutineScope()

            SettingsListItem(
                headlineContent = "恢复初始化",
                onSelected = {
                    coroutineScope.launch {
                        SP.clear()
                        Snackbar.show("已恢复初始化")
                    }
                },
            )
        }
    }
}