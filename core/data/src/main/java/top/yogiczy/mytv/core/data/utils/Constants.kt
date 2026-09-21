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
     *
     * 这里都是内置源，用户自己添加的订阅另存在配置里，界面上会把两者合并展示。
     *
     * 只有「默认直播源」参与线路测速排序（每个频道十几条线路，值得测）；
     * 斗鱼/虎牙/YY 这类订阅每个频道只有一条线路、频道数却有上千个，
     * 测速既没有收益又会长时间占满带宽，因此固定不参与。
     */
    /**
     * 潮汕节目回放（自建订阅镜像）
     *
     * 内容是汕头台各栏目的点播回看（mp4 直链），不是直播频道。两种用法：
     * ① 作为内置订阅源，在「换源」/「自定义直播源」里直接选用；
     * ② 浏览「默认直播源」时以附加分组的形式挂在分组末尾
     *   （由 Configs.iptvChaoshanSourceEnable 控制，默认关闭）。
     */
    val CHAOSHAN_REPLAY_SOURCE = IptvSource(
        name = "潮汕节目回放",
        url = "https://gitee.com/nid123/chaoshan-tv/raw/master/data/chaoshan.txt",
    )

    val IPTV_SOURCE_LIST = IptvSourceList(
        listOf(
            IptvSource(
                name = "默认直播源",
                url = "https://tvlive.nide.qzz.io",
                lineSpeedSort = true,
            ),
            IptvSource(
                name = "斗鱼直播",
                url = "https://sub.ottiptv.cc/douyuyqk.m3u",
            ),
            IptvSource(
                name = "虎牙直播",
                url = "https://sub.ottiptv.cc/huyayqk.m3u",
            ),
            IptvSource(
                name = "YY轮播",
                url = "https://sub.ottiptv.cc/yylunbo.m3u",
            ),
            CHAOSHAN_REPLAY_SOURCE,
        )
    )

    /** 该订阅源是否内置的「潮汕节目回放」（点播回看，不显示台标、不参与测速） */
    fun isChaoshanReplaySource(source: IptvSource): Boolean =
        normalizeIptvSource(source).url == CHAOSHAN_REPLAY_SOURCE.url

    /**
     * 把配置里存的直播源对齐到内置源
     *
     * 内置源的属性（比如是否参与测速）会随着版本调整，老配置里存的还是旧值，
     * 按链接匹配到内置源就以内置的为准，匹配不到（用户自己的订阅）则原样保留。
     */
    fun normalizeIptvSource(source: IptvSource): IptvSource =
        IPTV_SOURCE_LIST.firstOrNull { it.url == source.url } ?: source

    /**
     * IPTV源缓存时间（毫秒）
     */
    const val IPTV_SOURCE_CACHE_TIME = 1000 * 60 * 60 * 24L // 24小时

    /**
     * 节目单来源
     *
     * 第一个是默认源，其余作为「补齐源」：默认源对某些频道没有节目时，
     * 会自动从补齐源里取这些频道的节目（见 EpgRepository）。
     */
    val EPG_SOURCE_LIST = EpgSourceList(
        listOf(
            /**
             * 老张的EPG，国内（四川电信）直连可达，作为默认节目单。
             *
             * 这里必须用实时生成的 `e.xml`：同目录下的 `e.xml.gz` 是预生成文件，
             * 拿到手时内容可能已经停在生成时刻，凌晨就会出现「当前时刻查不到节目」的断档。
             */
            EpgSource(
                name = "默认节目单 老张的EPG",
                url = "http://epg.51zmt.top:8000/e.xml",
            ),
            /**
             * 备用/补齐节目单：频道更多（含翡翠台等），国内可直连
             */
            EpgSource(
                name = "备用节目单 112114",
                url = "https://epg.112114.xyz/pp.xml",
            ),
            /**
             * 备用/补齐节目单：覆盖天数更长，海外站点
             */
            EpgSource(
                name = "备用节目单 Fanmingming",
                url = "https://live.fanmingming.com/e.xml",
            ),
        )
    )

    /**
     * 历史默认节目单地址
     *
     * 老配置里存的是这个预生成地址，读取配置时会被迁移成实时地址。
     */
    const val EPG_SOURCE_LEGACY_URL = "http://epg.51zmt.top:8000/e.xml.gz"

    /**
     * 节目单刷新时间阈值（小时）
     *
     * 在这个时间点之前优先沿用已有缓存，避免凌晨去拉源站还没生成的数据；
     * 但不会因此返回空节目单——缓存里当前时刻查不到节目时仍会照常重新拉取。
     */
    const val EPG_REFRESH_TIME_THRESHOLD = 2

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