package top.yogiczy.mytv.tv.utlis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 「收到安装包」提示状态
 *
 * 推送把 APK 传上来之后，原来直接就去调系统安装器：调不起来时用户只看到一句失败提示，
 * 连「东西到底收到没有」都不清楚。现在改成——先把「收到安装包」这个状态暴露给界面，
 * 由界面弹一个确认框（当贝市场那种体验），用户点「立即安装」再去装。
 *
 * HTTP 推送服务收到文件后调 [request]，界面（MainContent）读 [pending] 决定是否显示弹窗。
 * 状态放在 object 里，是因为推送服务不在 Compose 的作用域内。
 */
object ApkInstallPrompt {
    /** 待确认安装的安装包；null 表示当前没有弹窗 */
    var pending: ApkItem? by mutableStateOf(null)
        private set

    /** 最近一次安装尝试的失败原因（显示在弹窗里） */
    var lastError: String? by mutableStateOf(null)
        private set

    /** 请求弹出「是否安装」确认框 */
    fun request(item: ApkItem) {
        lastError = null
        pending = item
    }

    /** 记录一次失败（弹窗保持显示，把原因告诉用户） */
    fun fail(reason: String) {
        lastError = reason
    }

    /** 关闭弹窗 */
    fun clear() {
        pending = null
        lastError = null
    }
}
