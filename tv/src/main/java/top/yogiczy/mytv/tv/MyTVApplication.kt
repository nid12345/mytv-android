package top.yogiczy.mytv.tv

import android.app.Application
import top.yogiczy.mytv.core.data.AppData
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.tv.ui.utils.Configs

class MyTVApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        AppData.init(applicationContext)

        // 尽早装上崩溃记录：出问题时能在「设置 → 日志」里看到堆栈
        CrashReporter.install(this)
        reportLastCrashIfAny()

        // 升级后把新版本的默认设置落到老配置上一次
        Configs.migrateIfNeeded()

        UnsafeTrustManager.enableUnsafeTrustManager()
    }

    /** 把上次异常退出的堆栈摘要写进应用内日志，用户不必连电脑也能读出来 */
    private fun reportLastCrashIfAny() {
        val crash = CrashReporter.consumeLastCrash(this) ?: return

        // 完整堆栈很长，取「异常行 + 前几层调用」拼成一条可读的摘要
        val brief = crash.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("时间:") && !it.startsWith("线程:") }
            .take(6)
            .joinToString(" ← ")

        Logger.create("上次异常退出").e(brief.take(600))
    }
}
