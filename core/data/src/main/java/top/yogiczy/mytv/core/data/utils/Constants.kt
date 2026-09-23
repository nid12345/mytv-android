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
     * 第一个是默认源，其余作为「备用源」：
     * - 默认源整体不可用时，自动改用能用的备用源（见 EpgRepository）；
     * - 默认源对某些频道没有节目时，也会从备用源补齐。
     *
     * 这里**不要**只放一个源——节目单接口的可用性很不稳定（2026-09 实测：
     * 老牌的 `epg.51zmt.top:8000/e.xml` 已经 302 跳到一个域名停放页，返回的是 HTML，
     * 解析必然失败），多堆几个源才有容错空间。
     *
     * 各源实测情况（2026-09-23，本机网络）：
     * - 112114 主站：本机不可达，但它是国内最常用、更新最勤的一份，排第一
     * - 112114 CDN 转发（epg.163189.xyz）：✔ 可用，约 3MB / 2.7s
     * - EPG.pw：✔ 可用，约 6.4MB / 4.1s
     * - Fanmingming：本机不可达，保留作为覆盖
     * - 老张的 EPG（51zmt）：✘ 已失效，从内置列表移除（老配置会在读取时自动迁移走）
     */
    val EPG_SOURCE_LIST = EpgSourceList(
        listOf(
            EpgSource(
                name = "默认节目单 112114",
                url = "https://epg.112114.xyz/pp.xml",
            ),
            EpgSource(
                name = "备用节目单 112114 CDN转发",
                url = "https://epg.163189.xyz/pp.xml",
            ),
            EpgSource(
                name = "备用节目单 Fanmingming",
                url = "https://live.fanmingming.com/e.xml",
            ),
            EpgSource(
                name = "备用节目单 EPG.pw",
                url = "https://epg.pw/xmltv/epg_CN.xml",
            ),
        )
    )

    /**
     * 历史默认节目单地址
     *
     * 老配置里存的可能是这些地址：`.gz` 是服务器预生成的旧文件（内容常停在生成时刻），
     * 去掉 `.gz` 的实时地址后来也失效了（跳转到一个停放页、返回 HTML）。
     * 读取配置时统一迁移到 [EPG_SOURCE_LIST] 的第一个。
     */
    val EPG_SOURCE_LEGACY_URLS = setOf(
        "http://epg.51zmt.top:8000/e.xml",
        "http://epg.51zmt.top:8000/e.xml.gz",
    )

    /** 兼容旧调用：单独要一个「老的 .gz 地址」时用（历史上被写进过配置） */
    const val EPG_SOURCE_LEGACY_URL = "http://epg.51zmt.top:8000/e.xml.gz"

    /**
     * 节目单下载超时（毫秒）
     *
     * 一份完整节目单动辄 3~6MB，电视盒子上慢速网络要几十秒；
     * 默认的 10 秒读超时会让大源直接失败，这里放宽。
     */
    const val EPG_FETCH_CONNECT_TIMEOUT = 15_000L
    const val EPG_FETCH_READ_TIMEOUT = 60_000L


    /**
     * 节目单刷新时间阈值（小时）
     *
     * 在这个时间点之前优先沿用已有缓存，避免凌晨去拉源站还没生成的数据；
     * 但不会因此返回空节目单——缓存里当前时刻查不到节目时仍会照常重新拉取。
     */
    const val EPG_REFRESH_TIME_THRESHOLD = 2

    /**
     * 应用更新（走定制版自己的通道）
     *
     * 上游官方的更新接口发的是官方签名包（包名也不同，装上就是另一个应用），
     * 定制版必须查自己的 Release：tag 形如 `custom-v1.7`，附件就是定制版 APK。
     *
     * GitHub 的接口与附件下载在国内经常直连不上，所以检查与下载都准备了镜像回退，
     * 实测可用的加速前缀见 [GITHUB_MIRROR_GH_PROXY] / [GITHUB_MIRROR_GH_PROXY_NET]。
     */
    const val CUSTOM_RELEASE_REPO = "nid12345/mytv-android"

    /** GitHub 加速前缀：gh-proxy 同时支持接口与附件 */
    const val GITHUB_MIRROR_GH_PROXY = "https://gh-proxy.com/"

    /** GitHub 加速前缀：ghproxy 只代理文件（接口返回 403），仅用于下载回退 */
    const val GITHUB_MIRROR_GH_PROXY_NET = "https://ghproxy.net/"

    /** APK 下载可用的全部镜像前缀（不含直连） */
    val RELEASE_DOWNLOAD_MIRRORS = listOf(
        GITHUB_MIRROR_GH_PROXY,
        GITHUB_MIRROR_GH_PROXY_NET,
    )

    /**
     * 更新检查地址
     *
     * 每一项都是一串地址，「按顺序尝试，第一个成功的就算数」：
     * stable 取最新正式版，beta 取最近的发布（含预发布）。
     */
    val RELEASE_CHECK_URLS = mapOf(
        "stable" to releaseCheckUrls("releases/latest"),
        "beta" to releaseCheckUrls("releases?per_page=5"),
    )

    private fun releaseCheckUrls(path: String): List<String> {
        val api = "https://api.github.com/repos/$CUSTOM_RELEASE_REPO/$path"
        // ghproxy.net 对 api.github.com 返回 403，所以接口链里只用 gh-proxy
        return listOf(api, GITHUB_MIRROR_GH_PROXY + api)
    }

    /**
     * 更新通道旧址（上游官方接口，已不再使用）
     *
     * 保留仅为兼容/参考：那条通道发的是官方签名包，定制版不能走。
     */
    @Deprecated("改用 RELEASE_CHECK_URLS（定制版自己的 Release）")
    val GIT_RELEASE_LATEST_URL = mapOf(
        "stable" to "https://raw.githubusercontent.com/yaoxieyoulei/mytv-android-update/main/tv-stable.json",
        "beta" to "https://raw.githubusercontent.com/yaoxieyoulei/mytv-android-update/main/tv-beta.json",
    )

    /**
     * GitHub加速代理地址
     *
     * 原来的 `ghp.ci` 已经停止服务（实测连接失败），会让下载地址变成死链，
     * 这里留空表示直连；需要加速时由下载流程按 [RELEASE_DOWNLOAD_MIRRORS] 逐条回退。
     */
    const val GITHUB_PROXY = ""

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