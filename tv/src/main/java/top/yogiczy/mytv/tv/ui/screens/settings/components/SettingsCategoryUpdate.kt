package top.yogiczy.mytv.tv.ui.screens.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Switch
import top.yogiczy.mytv.tv.ui.screens.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screens.update.UpdateViewModel
import top.yogiczy.mytv.tv.ui.utils.Configs

@Composable
fun SettingsCategoryUpdate(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = viewModel(),
    updateViewModel: UpdateViewModel = viewModel(),
) {
    SettingsContentList(modifier) {
        item {
            SettingsListItem(
                modifier = Modifier.focusRequester(it),
                headlineContent = "检查更新",
                supportingContent = when {
                    updateViewModel.isChecking -> "正在检查…"
                    updateViewModel.hasChecked ->
                        "最新版本：v${updateViewModel.latestRelease.version}" +
                                if (updateViewModel.isUpdateAvailable) "（发现新版本）" else "（已是最新）"
                    else -> "点按立即检查是否有新版本"
                },
                trailingContent = "检查",
                onSelected = {
                    // 计数 +1 让更新面板强制重查一次，再把面板弹出来
                    updateViewModel.manualCheckRequest += 1
                    updateViewModel.visible = true
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "升级提醒",
                supportingContent = if (settingsViewModel.updateRemindEnable)
                    "打开应用时自动检查，发现新版本会提醒"
                else "已关闭，只在上面「检查更新」里手动查看",
                trailingContent = {
                    Switch(settingsViewModel.updateRemindEnable, null)
                },
                onSelected = {
                    settingsViewModel.updateRemindEnable = !settingsViewModel.updateRemindEnable
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "更新强提醒",
                supportingContent = if (settingsViewModel.updateForceRemind) "检测到新版本时会全屏提醒"
                else "检测到新版本时仅消息提示",
                trailingContent = {
                    Switch(settingsViewModel.updateForceRemind, null)
                },
                onSelected = {
                    settingsViewModel.updateForceRemind = !settingsViewModel.updateForceRemind
                },
            )
        }

        item {
            val list = mapOf(
                "stable" to "稳定版",
                "beta" to "测试版",
            )

            SettingsListItem(
                headlineContent = "更新通道",
                supportingContent = if (settingsViewModel.updateChannel == "beta")
                    "检查最近的发布（含测试版）"
                else "检查最新的正式发布",
                trailingContent = list[settingsViewModel.updateChannel] ?: "",
                onSelected = {
                    settingsViewModel.updateChannel =
                        list.keys.first { it != settingsViewModel.updateChannel }
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "下载线路",
                supportingContent = when (settingsViewModel.updateDownloadRoute) {
                    Configs.UpdateDownloadRoute.AUTO -> "先直连 GitHub，失败自动改用加速镜像"
                    Configs.UpdateDownloadRoute.DIRECT -> "只从 GitHub 直连下载"
                    Configs.UpdateDownloadRoute.MIRROR -> "优先走加速镜像，失败再直连"
                },
                trailingContent = settingsViewModel.updateDownloadRoute.label,
                onSelected = {
                    val routes = Configs.UpdateDownloadRoute.entries
                    settingsViewModel.updateDownloadRoute =
                        routes[(routes.indexOf(settingsViewModel.updateDownloadRoute) + 1) % routes.size]
                },
            )
        }
    }
}
