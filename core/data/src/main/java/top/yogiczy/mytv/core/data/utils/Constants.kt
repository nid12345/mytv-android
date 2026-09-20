package top.yogiczy.mytv.core.data.utils

import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSourceList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList

/**
 * 常量
 */
object Constants {
    /**
     * 应用 标题
     */
    const val APP_TITLE = "MyTV"

    /**
     * 应用 代码仓库
     */
    const val APP_REPO = "https://github.com/yaoxieyoulei/mytv-android"

    /**
     * 频道台标来源
     *
     * 指向自建的台标镜像仓库（内容取自 fanmingming/live 的 tv 目录），经公共 CDN 加速。
     * 频道数据里的台标地址由这里生成，实际加载时的多级回退顺序见 ChannelItemLogo：
     * 内置台标 → 本仓库 CDN → 本仓库直连 → 上游原站 → 上游国内镜像。
     */
    const val CHANNEL_LOGO_SOURCE = "https://gcore.jsdelivr.net/gh/nid12345/mytv-logos@main/tv"

    /**
     * IPTV直播源
     */
    val IPTV_SOURCE_LIST = IptvSourceList(
        listOf(
            IptvSource(
                name = "默认直播源",
                url = "https://tvlive.nide.qzz.io",
            ),
        )
    )

    /**
     * IPTV源缓存时间（毫秒）
     */
    const val IPTV_SOURCE_CACHE_TIME = 1000 * 60 * 60 * 24L // 24小时

    /**
     * 节目单来源
     */
    val EPG_SOURCE_LIST = EpgSourceList(
        listOf(
            /**
             * 国内（四川电信）直连可达，作为默认节目单
             */
            EpgSource(
                name = "默认节目单 老张的EPG",
                url = "http://epg.51zmt.top:8000/e.xml.gz",
            ),
            /**
             * 备用节目单：频道更多，大陆网络下作为可切换的备选
             */
            EpgSource(
                name = "备用节目单 112114",
                url = "https://epg.112114.xyz/pp.xml",
            ),
            /**
             * 备用节目单：海外站点，作为最后的备选
             */
            EpgSource(
                name = "备用节目单 Fanmingming",
                url = "https://live.fanmingming.com/e.xml",
            ),
        )
    )

    /**
     * 节目单刷新时间阈值（小时）
     */
    const val EPG_REFRESH_TIME_THRESHOLD = 2 // 不到2点不刷新

    /**
     * Git最新版本信息
     */
    val GIT_RELEASE_LATEST_URL = mapOf(
        "stable" to "https://ghp.ci/https://raw.githubusercontent.com/yaoxieyoulei/mytv-android-update/main/tv-stable.json",
        "beta" to "https://ghp.ci/https://raw.githubusercontent.com/yaoxieyoulei/mytv-android-update/main/tv-beta.json",
    )

    /**
     * GitHub加速代理地址
     */
    const val GITHUB_PROXY = "https://ghp.ci/"

    /**
     * HTTP请求重试次数
     */
    const val HTTP_RETRY_COUNT = 10L

    /**
     * HTTP请求重试间隔时间（毫秒）
     */
    const val HTTP_RETRY_INTERVAL = 3000L

    /**
     * 播放器 userAgent
     */
    const val VIDEO_PLAYER_USER_AGENT = "ExoPlayer"

    /**
     * 播放器加载超时
     */
    const val VIDEO_PLAYER_LOAD_TIMEOUT = 1000L * 15 // 15秒

    /**
     * 日志历史最大保留条数
     */
    const val LOG_HISTORY_MAX_SIZE = 50

    /**
     * 界面 临时频道界面显示时间
     */
    const val UI_TEMP_CHANNEL_SCREEN_SHOW_DURATION = 1500L // 1.5秒

    /**
     * 界面 超时未操作自动关闭界面
     */
    const val UI_SCREEN_AUTO_CLOSE_DELAY = 1000L * 15 // 15秒

    /**
     * 界面 时间显示前后范围
     */
    const val UI_TIME_SCREEN_SHOW_DURATION = 1000L * 30 // 前后30秒
}