package top.yogiczy.mytv.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import top.yogiczy.mytv.tv.utlis.ApkInstallPrompt

/**
 * 安装会话的结果回调
 *
 * [android.content.pm.PackageInstaller.Session.commit] 之后，系统会把这个广播发回来：
 * - `STATUS_PENDING_USER_ACTION`：系统在等用户确认，**extra 里带着「确认界面」的 Intent**，
 *   必须由我们把它拉起来，安装界面才会出现（不处理的话就是「点了安装毫无反应」）；
 * - `STATUS_SUCCESS` / 其它：安装完成或失败，给用户一条消息。
 */
class ApkInstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE
        )
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()

        log.i("安装会话回调：status=$status message=$statusMessage")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmIntent =
                    intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent

                if (confirmIntent == null) {
                    Snackbar.show("安装界面调起失败：系统没有返回确认界面", type = SnackbarType.ERROR)
                    return
                }

                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirmIntent) }
                    .onFailure {
                        Snackbar.show(
                            "安装界面调起失败：${it.javaClass.simpleName}",
                            type = SnackbarType.ERROR,
                        )
                    }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                ApkInstallPrompt.clear()
                Snackbar.show(
                    if (fileName.isBlank()) "安装完成" else "安装完成：$fileName"
                )
            }

            else -> Snackbar.show(
                "安装失败：${statusMessage ?: "错误码 $status"}",
                type = SnackbarType.ERROR,
                duration = 5000,
            )
        }
    }

    companion object {
        const val EXTRA_FILE_NAME = "extra_file_name"

        private val log = Logger.create("ApkInstallResultReceiver")
    }
}
