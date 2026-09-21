package top.yogiczy.mytv.tv

import android.app.Application
import top.yogiczy.mytv.core.data.AppData
import top.yogiczy.mytv.tv.ui.utils.Configs

class MyTVApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        AppData.init(applicationContext)

        // 升级后把新版本的默认设置落到老配置上一次
        Configs.migrateIfNeeded()

        UnsafeTrustManager.enableUnsafeTrustManager()
    }
}
