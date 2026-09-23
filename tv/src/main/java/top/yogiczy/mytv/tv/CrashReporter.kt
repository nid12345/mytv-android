package top.yogiczy.mytv.tv

import android.content.Context
import android.util.Log
import top.yogiczy.mytv.core.data.utils.Logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃记录
 *
 * 电视/盒子上出了问题没法连电脑看 logcat，所以把未捕获异常的堆栈落到应用私有目录里，
 * 下次启动时重新注入到应用内日志（设置 → 日志），用户照着念就能定位。
 *
 * 只记录，不上报、不弹窗，不影响正常流程。
 */
object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val FILE_NAME = "last_crash.txt"

    /** 安装未捕获异常处理器（必须在 Application.onCreate 里尽早调用） */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(appContext, thread, throwable) }
            // 交回系统默认处理（弹「应用已停止运行」等），不要吞掉
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 取出上次运行留下的崩溃记录，顺便清理
     *
     * @return 崩溃摘要（异常类型 + 消息 + 前几层调用栈），没有则返回 null
     */
    fun consumeLastCrash(context: Context): String? = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return@runCatching null

        val text = file.readText()
        file.delete()
        text.ifBlank { null }
    }.getOrNull()

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val stack = Log.getStackTraceString(throwable)

        val text = buildString {
            appendLine("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("线程: ${thread.name}")
            appendLine("异常: ${throwable.javaClass.name}")
            appendLine("消息: ${throwable.message}")
            appendLine(stack)
        }

        File(context.filesDir, FILE_NAME).writeText(text)

        // 同时打到 logcat（能连电脑时更方便）
        Log.e(TAG, "应用异常退出", throwable)
    }
}
