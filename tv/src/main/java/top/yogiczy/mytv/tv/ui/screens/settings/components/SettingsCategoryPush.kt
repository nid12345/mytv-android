package top.yogiczy.mytv.tv.ui.screens.settings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.material.LocalPopupManager
import top.yogiczy.mytv.tv.ui.material.SimplePopup
import top.yogiczy.mytv.tv.ui.screens.apkmanager.ApkManageScreen
import top.yogiczy.mytv.tv.ui.screens.components.Qrcode
import top.yogiczy.mytv.tv.utlis.HttpServer

@Composable
fun SettingsCategoryPush(
    modifier: Modifier = Modifier,
) {
    val serverUrl: String = HttpServer.serverUrl

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Qrcode(
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .width(200.dp)
                    .height(200.dp),
                textProvider = { serverUrl },
            )

            Text("服务已启动：${serverUrl}")
            Text("请扫描二维码或输入IP地址进行连接")
        }

        val popupManager = LocalPopupManager.current
        val focusRequester = remember { FocusRequester() }
        var isApkManageVisible by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SettingsListItem(
                modifier = Modifier
                    .width(320.dp)
                    .focusRequester(focusRequester),
                headlineContent = "安装包管理",
                supportingContent = "安装、删除推送收到的安装包；卸载已装应用",
                onSelected = {
                    popupManager.push(focusRequester, true)
                    isApkManageVisible = true
                },
            )
        }

        SimplePopup(
            visibleProvider = { isApkManageVisible },
            onDismissRequest = { isApkManageVisible = false },
        ) {
            ApkManageScreen(onClose = { isApkManageVisible = false })
        }
    }
}
