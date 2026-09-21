package top.yogiczy.mytv.core.util.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

object ApkInstaller {
    private const val APK_MIME = "application/vnd.android.package-archive"

    /**
     * 调起系统安装界面
     *
     * 不同 ROM 对安装 Intent 的支持不一样，所以依次尝试几种写法：
     * ① `ACTION_VIEW` + apk MIME（原生 Android、绝大多数盒子）；
     * ② `ACTION_INSTALL_PACKAGE` + `EXTRA_NOT_UNKNOWN_SOURCE`（部分定制 ROM / 电视盒只认这个）。
     *
     * Android 7 及以上走 FileProvider（授权系统安装器读取应用私有目录）；
     * 7 以下没有可靠的 FileProvider 授权链路，把安装包复制到外部缓存并放开读/执行权限。
     *
     * @return 成功调起返回 null，否则返回失败原因（可直接展示给用户）
     */
    @SuppressLint("SetWorldReadable")
    @Suppress("DEPRECATION")
    fun installApk(context: Context, filePath: String): String? {
        val file = File(filePath)
        if (!file.exists()) return "安装包不存在：${file.name}"

        val uri = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(context, context.packageName + ".FileProvider", file)
            } else {
                val stageDir = context.externalCacheDir ?: context.cacheDir
                val stagedApkFile = File(stageDir, file.name).apply {
                    writeBytes(file.readBytes())
                    // 解决 Android 6 无法解析应用私有目录里的安装包
                    setReadable(true, false)
                    setExecutable(true, false)
                }
                Uri.fromFile(stagedApkFile)
            }
        } catch (ex: Exception) {
            return "无法读取安装包：${ex.message ?: ex.javaClass.simpleName}"
        }

        val candidates = listOf(
            "VIEW" to Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME),
            "INSTALL_PACKAGE" to Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(uri, APK_MIME)
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true),
        )

        val errors = mutableListOf<String>()
        for ((name, intent) in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                context.startActivity(intent)
                return null
            } catch (ex: Exception) {
                errors += "$name: ${ex.javaClass.simpleName}(${ex.message.orEmpty()})"
            }
        }

        // 走到这里说明系统里没有能响应的安装器：多半是「安装未知应用」没放行
        if (!canRequestPackageInstalls(context)) {
            return "系统未允许本应用安装未知应用，请先在设置里放行后重试。${errors.joinToString("; ")}"
        }
        return errors.joinToString("; ").ifBlank { "系统没有可用的安装器" }
    }

    /** 是否已允许本应用安装未知来源的应用（Android 8 以下恒为 true） */
    fun canRequestPackageInstalls(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    /** 跳到「允许安装未知应用」设置页（部分 ROM 没有该页面时返回 false） */
    fun openUnknownSourceSetting(context: Context): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (ex: Exception) {
        false
    }
}
