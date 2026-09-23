package top.yogiczy.mytv.tv.utlis

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import top.yogiczy.mytv.core.data.utils.Loggable
import top.yogiczy.mytv.core.util.utils.ApkInstaller
import top.yogiczy.mytv.tv.ApkInstallResultReceiver
import java.io.File

/**
 * 安装 APK（多级策略）
 *
 * 之前只走「Intent 调系统安装器」一条路，部分电视盒 / 定制 ROM 上会直接报「调不起来」。
 * 这里改成三级，逐级回退：
 *
 * 1. **PackageInstaller 会话**（应用商店同款路径）：直接把 APK 写进系统安装会话，
 *    由系统给出确认界面。不经过 Intent 解析、不依赖某个安装器 Activity 是否存在、
 *    也不需要 FileProvider 授权，兼容性最好；
 * 2. `ACTION_VIEW` + apk MIME（原生 Android、绝大多数盒子）；
 * 3. `ACTION_INSTALL_PACKAGE` + `EXTRA_NOT_UNKNOWN_SOURCE`（只认这个的定制 ROM）。
 *
 * 另外 Android 8 起必须先放行「安装未知应用」，没放行时任何方式都调不起来——
 * 这种情况返回 [NEED_UNKNOWN_SOURCE_PERMISSION]，由界面提示用户去设置里放行。
 */
object ApkInstallHelper : Loggable() {

    /** 「系统未放行安装未知应用」专用标记：界面据此给一键去设置的入口 */
    const val NEED_UNKNOWN_SOURCE_PERMISSION = "__NEED_UNKNOWN_SOURCE_PERMISSION__"

    /**
     * 安装 APK
     *
     * @return 成功调起返回 null，否则返回失败原因（可直接展示给用户）
     */
    fun install(context: Context, filePath: String): String? {
        val file = File(filePath)
        if (!file.exists()) return "安装包不存在：${file.name}"

        if (!ApkInstaller.canRequestPackageInstalls(context)) {
            log.i("未放行「安装未知应用」，无法调起安装")
            return NEED_UNKNOWN_SOURCE_PERMISSION
        }

        val errors = mutableListOf<String>()

        // ① 会话安装：兼容性最好的一条路
        runCatching { installBySession(context, file) }
            .onSuccess {
                log.i("已提交安装会话（${file.name}）")
                return null
            }
            .onFailure {
                log.w("会话安装不可用，改用安装 Intent", it)
                errors += "会话安装：${describe(it)}"
            }

        // ② / ③ 传统的安装 Intent
        val legacyError = ApkInstaller.installApk(context, filePath)
        if (legacyError == null) return null
        errors += legacyError

        return errors.joinToString("；")
    }

    /**
     * 通过 [PackageInstaller] 会话安装
     *
     * `commit()` 的结果（尤其 `STATUS_PENDING_USER_ACTION`，系统会把「用户确认」的
     * Intent 塞回来）由 [ApkInstallResultReceiver] 处理——**必须把这个 Intent 拉起来**，
     * 否则系统只是在等一个永远不会出现的确认，表现就是「点了安装没反应」。
     */
    private fun installBySession(context: Context, file: File) {
        val packageInstaller = context.packageManager.packageInstaller

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

        val sessionId = packageInstaller.createSession(params)

        try {
            packageInstaller.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }

                val resultIntent = Intent(context, ApkInstallResultReceiver::class.java)
                    .putExtra(ApkInstallResultReceiver.EXTRA_FILE_NAME, file.name)

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                session.commit(pendingIntent.intentSender)
            }
        } catch (ex: Exception) {
            // 提交失败要把会话作废，否则会残留一个空会话
            runCatching { packageInstaller.abandonSession(sessionId) }
            throw ex
        }
    }

    /** 把异常压成一句能读的短说明（带上具体原因，便于定位是哪一级失败） */
    private fun describe(ex: Throwable): String =
        "${ex.javaClass.simpleName}(${ex.message.orEmpty().take(60)})"
}
