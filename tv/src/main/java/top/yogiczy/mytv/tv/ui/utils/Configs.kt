package top.yogiczy.mytv.tv.ui.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.FavoriteChannel
import top.yogiczy.mytv.core.data.entities.channel.FavoriteChannelList
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserveList
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSourceList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.SP
import top.yogiczy.mytv.tv.ui.screens.videoplayer.VideoPlayerDisplayMode

/**
 * 应用配置
 */
object Configs {
    /**
     * 推送写入配置的版本号
     *
     * 手机扫码网页/HTTP 接口改配置时只会写进存储，内存里的界面状态不会跟着变，
     * 表现就是「推送完必须重启应用才生效」。这里递增一个版本号，
     * 运行中的界面收到变化后自己重新读一遍配置。
     */
    val configPushVersion = MutableStateFlow(0L)

    /** 收到新订阅源（推送/添加）时 +1，界面据此立即切过去并重载频道列表 */
    val iptvSourcePushVersion = MutableStateFlow(0L)

    /** 普通配置推送：让界面重新读取配置 */
    fun notifyConfigPushed() {
        configPushVersion.value += 1
    }

    /** 订阅源推送：重新读取配置，并触发频道列表重载 */
    fun notifyIptvSourcePushed() {
        configPushVersion.value += 1
        iptvSourcePushVersion.value += 1
    }

    enum class KEY {
        /** ==================== 应用 ==================== */
        /** 开机自启 */
        APP_BOOT_LAUNCH,

        /** 配置版本，用于把新版本的默认值应用到老配置上 */
        APP_CONFIG_VERSION,

        /** 上一次最新版本 */
        APP_LAST_LATEST_VERSION,

        /** 协议已同意 */
        APP_AGREEMENT_AGREED,

        /** ==================== 调式 ==================== */
        /** 显示fps */
        DEBUG_SHOW_FPS,

        /** 播放器详细信息 */
        DEBUG_SHOW_VIDEO_PLAYER_METADATA,

        /** 显示布局网格 */
        DEBUG_SHOW_LAYOUT_GRIDS,

        /** ==================== 直播源 ==================== */
        /** 上一次频道序号 */
        IPTV_LAST_CHANNEL_IDX,

        /** 启动时是否自动播放上次退出的频道 */
        IPTV_LAST_CHANNEL_REMEMBER_ENABLE,

        /** 换台反转 */
        IPTV_CHANNEL_CHANGE_FLIP,

        /** 当前直播源 */
        IPTV_SOURCE_CURRENT,

        /** 直播源列表 */
        IPTV_SOURCE_LIST,

        /** 直播源缓存时间（毫秒） */
        IPTV_SOURCE_CACHE_TIME,

        /** 直播源可播放host列表 */
        IPTV_PLAYABLE_HOST_LIST,

        /** 是否启用数字选台 */
        IPTV_CHANNEL_NO_SELECT_ENABLE,

        /** 是否启用直播源频道收藏 */
        IPTV_CHANNEL_FAVORITE_ENABLE,

        /** 是否在频道分组栏顶部显示「换源」快速切换入口 */
        IPTV_SOURCE_QUICK_SWITCH_ENABLE,

        /** 是否启用线路测速排序 */
        IPTV_SOURCE_LINE_SPEED_SORT_ENABLE,

        /** 显示直播源频道收藏列表 */
        IPTV_CHANNEL_FAVORITE_LIST_VISIBLE,

        /** 直播源频道收藏列表（旧，只存频道名） */
        IPTV_CHANNEL_FAVORITE_LIST,

        /** 直播源频道收藏列表（新，含线路快照，跨订阅源） */
        IPTV_CHANNEL_FAVORITE_ITEMS,

        /** 直播源频道收藏换台边界跳出 */
        IPTV_CHANNEL_FAVORITE_CHANGE_BOUNDARY_JUMP_OUT,

        /** 直播源分组隐藏列表（旧，全局一份） */
        IPTV_CHANNEL_GROUP_HIDDEN_LIST,

        /** 直播源分组隐藏列表（新，按订阅源地址分别记忆） */
        IPTV_SOURCE_GROUP_HIDDEN_MAP,

        /** 各订阅源的分组名缓存（用于跨源分组统一管理） */
        IPTV_SOURCE_GROUP_NAMES_MAP,

        /** 是否显示内置的「潮汕节目回放」分组 */
        IPTV_CHAOSHAN_SOURCE_ENABLE,

        /** 混合模式 */
        IPTV_HYBRID_MODE,

        /** ==================== 节目单 ==================== */
        /** 启用节目单 */
        EPG_ENABLE,

        /** 当前节目单来源 */
        EPG_SOURCE_CURRENT,

        /** 节目单来源列表 */
        EPG_SOURCE_LIST,

        /** 节目单刷新时间阈值（小时） */
        EPG_REFRESH_TIME_THRESHOLD,

        /** 节目预约列表 */
        EPG_CHANNEL_RESERVE_LIST,

        /** ==================== 界面 ==================== */
        /** 显示节目进度 */
        UI_SHOW_EPG_PROGRAMME_PROGRESS,

        /** 显示常驻节目进度 */
        UI_SHOW_EPG_PROGRAMME_PERMANENT_PROGRESS,

        /** 显示台标 */
        UI_SHOW_CHANNEL_LOGO,

        /** 使用经典选台界面 */
        UI_USE_CLASSIC_PANEL_SCREEN,

        /** 界面密度缩放比例 */
        UI_DENSITY_SCALE_RATIO,

        /** 界面字体缩放比例 */
        UI_FONT_SCALE_RATIO,

        /** 时间显示模式 */
        UI_TIME_SHOW_MODE,

        /** 焦点优化 */
        UI_FOCUS_OPTIMIZE,

        /** 自动关闭界面延时 */
        UI_SCREEN_AUTO_CLOSE_DELAY,

        /** ==================== 更新 ==================== */
        /** 更新强提醒 */
        UPDATE_FORCE_REMIND,

        /** 更新通道 */
        UPDATE_CHANNEL,

        /** ==================== 播放器 ==================== */
        /** 播放器 自定义ua */
        VIDEO_PLAYER_USER_AGENT,

        /** 播放器 加载超时 */
        VIDEO_PLAYER_LOAD_TIMEOUT,

        /** 播放器 显示模式 */
        VIDEO_PLAYER_DISPLAY_MODE,

        /** 按频道名记住的显示比例（频道名 → 模式值） */
        VIDEO_PLAYER_DISPLAY_MODE_CHANNEL_MAP,

        /** 播放器 强制音频软解 */
        VIDEO_PLAYER_FORCE_AUDIO_SOFT_DECODE,

        /** 播放器 渲染方式 */
        VIDEO_PLAYER_RENDER_MODE,

        /** 播放器 停止上一媒体项 */
        VIDEO_PLAYER_STOP_PREVIOUS_MEDIA_ITEM,

        /** 播放器 跳过同一VSync渲染多帧 */
        VIDEO_PLAYER_SKIP_MULTIPLE_FRAMES_ON_SAME_VSYNC,
    }

    /** ==================== 应用 ==================== */
    /** 开机自启 */
    var appBootLaunch: Boolean
        get() = SP.getBoolean(KEY.APP_BOOT_LAUNCH.name, false)
        set(value) = SP.putBoolean(KEY.APP_BOOT_LAUNCH.name, value)

    /** 上一次最新版本 */
    var appLastLatestVersion: String
        get() = SP.getString(KEY.APP_LAST_LATEST_VERSION.name, "")
        set(value) = SP.putString(KEY.APP_LAST_LATEST_VERSION.name, value)

    /** 协议已同意 */
    var appAgreementAgreed: Boolean
        get() = SP.getBoolean(KEY.APP_AGREEMENT_AGREED.name, false)
        set(value) = SP.putBoolean(KEY.APP_AGREEMENT_AGREED.name, value)

    /** ==================== 调式 ==================== */
    /** 显示fps */
    var debugShowFps: Boolean
        get() = SP.getBoolean(KEY.DEBUG_SHOW_FPS.name, false)
        set(value) = SP.putBoolean(KEY.DEBUG_SHOW_FPS.name, value)

    /** 播放器详细信息 */
    var debugShowVideoPlayerMetadata: Boolean
        get() = SP.getBoolean(KEY.DEBUG_SHOW_VIDEO_PLAYER_METADATA.name, false)
        set(value) = SP.putBoolean(KEY.DEBUG_SHOW_VIDEO_PLAYER_METADATA.name, value)

    /** 显示布局网格 */
    var debugShowLayoutGrids: Boolean
        get() = SP.getBoolean(KEY.DEBUG_SHOW_LAYOUT_GRIDS.name, false)
        set(value) = SP.putBoolean(KEY.DEBUG_SHOW_LAYOUT_GRIDS.name, value)

    /** ==================== 直播源 ==================== */
    /** 上一次直播源序号 */
    var iptvLastChannelIdx: Int
        get() = SP.getInt(KEY.IPTV_LAST_CHANNEL_IDX.name, 0)
        set(value) = SP.putInt(KEY.IPTV_LAST_CHANNEL_IDX.name, value)

    /** 启动时是否自动播放上次退出的频道（关闭则每次从第一个频道开始） */
    var iptvLastChannelRememberEnable: Boolean
        get() = SP.getBoolean(KEY.IPTV_LAST_CHANNEL_REMEMBER_ENABLE.name, true)
        set(value) = SP.putBoolean(KEY.IPTV_LAST_CHANNEL_REMEMBER_ENABLE.name, value)

    /** 换台反转 */
    var iptvChannelChangeFlip: Boolean
        get() = SP.getBoolean(KEY.IPTV_CHANNEL_CHANGE_FLIP.name, false)
        set(value) = SP.putBoolean(KEY.IPTV_CHANNEL_CHANGE_FLIP.name, value)

    /** 当前直播源 */
    var iptvSourceCurrent: IptvSource
        get() {
            val source = Json.decodeFromString<IptvSource>(
                SP.getString(KEY.IPTV_SOURCE_CURRENT.name, "")
                    .ifBlank { Json.encodeToString(Constants.IPTV_SOURCE_LIST.first()) }
            )

            // 内置源的属性会随版本调整（比如新增了「是否参与测速」），
            // 老配置里存的还是旧值，按链接对齐到内置源的定义
            return Constants.normalizeIptvSource(source)
        }
        set(value) = SP.putString(KEY.IPTV_SOURCE_CURRENT.name, Json.encodeToString(value))

    /** 直播源列表（用户自定义部分，内置源不存这里） */
    var iptvSourceList: IptvSourceList
        get() = Json.decodeFromString<IptvSourceList>(
            SP.getString(KEY.IPTV_SOURCE_LIST.name, Json.encodeToString(IptvSourceList()))
        ).let { IptvSourceList(it.map(Constants::normalizeIptvSource)) }
        set(value) = SP.putString(KEY.IPTV_SOURCE_LIST.name, Json.encodeToString(value))

    /** 直播源缓存时间（毫秒） */
    var iptvSourceCacheTime: Long
        get() = SP.getLong(KEY.IPTV_SOURCE_CACHE_TIME.name, Constants.IPTV_SOURCE_CACHE_TIME)
        set(value) = SP.putLong(KEY.IPTV_SOURCE_CACHE_TIME.name, value)

    /** 直播源可播放host列表 */
    var iptvPlayableHostList: Set<String>
        get() = SP.getStringSet(KEY.IPTV_PLAYABLE_HOST_LIST.name, emptySet())
        set(value) = SP.putStringSet(KEY.IPTV_PLAYABLE_HOST_LIST.name, value)

    /** 是否启用数字选台 */
    var iptvChannelNoSelectEnable: Boolean
        get() = SP.getBoolean(KEY.IPTV_CHANNEL_NO_SELECT_ENABLE.name, true)
        set(value) = SP.putBoolean(KEY.IPTV_CHANNEL_NO_SELECT_ENABLE.name, value)

    /** 是否在频道分组栏顶部显示「换源」快速切换入口 */
    var iptvSourceQuickSwitchEnable: Boolean
        get() = SP.getBoolean(KEY.IPTV_SOURCE_QUICK_SWITCH_ENABLE.name, true)
        set(value) = SP.putBoolean(KEY.IPTV_SOURCE_QUICK_SWITCH_ENABLE.name, value)

    /** 是否启用线路测速排序 */
    var iptvSourceLineSpeedSortEnable: Boolean
        get() = SP.getBoolean(KEY.IPTV_SOURCE_LINE_SPEED_SORT_ENABLE.name, true)
        set(value) = SP.putBoolean(KEY.IPTV_SOURCE_LINE_SPEED_SORT_ENABLE.name, value)

    /** 是否启用直播源频道收藏 */
    var iptvChannelFavoriteEnable: Boolean
        get() = SP.getBoolean(KEY.IPTV_CHANNEL_FAVORITE_ENABLE.name, true)
        set(value) = SP.putBoolean(KEY.IPTV_CHANNEL_FAVORITE_ENABLE.name, value)

    /** 显示直播源频道收藏列表 */
    var iptvChannelFavoriteListVisible: Boolean
        get() = SP.getBoolean(KEY.IPTV_CHANNEL_FAVORITE_LIST_VISIBLE.name, false)
        set(value) = SP.putBoolean(KEY.IPTV_CHANNEL_FAVORITE_LIST_VISIBLE.name, value)

    /**
     * 直播源频道收藏列表（含线路快照）
     *
     * 存的是频道快照而不只是频道名，切换订阅源后收藏依然能显示、能播放。
     * 首次读取时会把旧版本只存名字的收藏迁移过来（这种条目要等再次收藏到
     * 对应频道的订阅源时才会补上线路地址）。
     */
    var iptvChannelFavoriteItems: FavoriteChannelList
        get() {
            val raw = SP.getString(KEY.IPTV_CHANNEL_FAVORITE_ITEMS.name, "")
            if (raw.isNotBlank()) {
                runCatching { Json.decodeFromString<FavoriteChannelList>(raw) }
                    .onSuccess { return it }
            }

            val legacy = SP.getStringSet(KEY.IPTV_CHANNEL_FAVORITE_LIST.name, emptySet())
            return FavoriteChannelList(legacy.map { FavoriteChannel(name = it) })
        }
        set(value) {
            SP.putString(KEY.IPTV_CHANNEL_FAVORITE_ITEMS.name, Json.encodeToString(value))
            // 旧字段同步一份，保证旧版本回退读取、以及手机端页面推送时表现一致
            SP.putStringSet(
                KEY.IPTV_CHANNEL_FAVORITE_LIST.name,
                value.map { it.name }.toSet(),
            )
        }

    /**
     * 直播源频道收藏列表（只暴露频道名）
     *
     * 兼容既有调用方（频道界面按名字判断是否已收藏）与手机端配置推送。
     */
    var iptvChannelFavoriteList: Set<String>
        get() = iptvChannelFavoriteItems.map { it.name }.toSet()
        set(value) {
            val old = iptvChannelFavoriteItems
            iptvChannelFavoriteItems = FavoriteChannelList(
                value.map { name ->
                    old.firstOrNull { it.name == name } ?: FavoriteChannel(name = name)
                }
            )
        }

    /**
     * 把收藏里的频道快照对齐到当前订阅源的频道
     *
     * 收藏来自多个订阅源，只有在该源里出现过，才能补上/刷新它的线路地址。
     * 补上以后即使切走，也能继续播放。
     */
    fun syncFavoriteChannels(channelList: List<Channel>) {
        if (channelList.isEmpty()) return

        val items = iptvChannelFavoriteItems
        var changed = false

        val synced = items.map { favorite ->
            val channel = channelList.firstOrNull { it.name == favorite.name } ?: return@map favorite
            val fresh = FavoriteChannel(
                name = channel.name,
                epgName = channel.epgName,
                urlList = channel.urlList,
                logo = channel.logo,
            )
            if (fresh != favorite) changed = true
            fresh
        }

        if (changed) iptvChannelFavoriteItems = FavoriteChannelList(synced)
    }

    /** 直播源频道收藏换台边界跳出 */
    var iptvChannelFavoriteChangeBoundaryJumpOut: Boolean
        get() = SP.getBoolean(KEY.IPTV_CHANNEL_FAVORITE_CHANGE_BOUNDARY_JUMP_OUT.name, true)
        set(value) = SP.putBoolean(KEY.IPTV_CHANNEL_FAVORITE_CHANGE_BOUNDARY_JUMP_OUT.name, value)

    /**
     * 各订阅源记住的分组隐藏列表（订阅源地址 → 隐藏的分组名）
     *
     * 按源分别记忆：切换订阅源、重启应用后，只要分组名没变就保持原设定。
     */
    var iptvSourceGroupHiddenMap: Map<String, List<String>>
        get() = decodeStringListMap(
            SP.getString(KEY.IPTV_SOURCE_GROUP_HIDDEN_MAP.name, "{}")
        )
        set(value) = SP.putString(
            KEY.IPTV_SOURCE_GROUP_HIDDEN_MAP.name, Json.encodeToString(value)
        )

    /** 各订阅源出现过的分组名缓存（订阅源地址 → 分组名列表） */
    var iptvSourceGroupNamesMap: Map<String, List<String>>
        get() = decodeStringListMap(
            SP.getString(KEY.IPTV_SOURCE_GROUP_NAMES_MAP.name, "{}")
        )
        set(value) = SP.putString(
            KEY.IPTV_SOURCE_GROUP_NAMES_MAP.name, Json.encodeToString(value)
        )

    /**
     * 直播源分组隐藏列表（当前订阅源视图）
     *
     * 读写都落在 [iptvSourceGroupHiddenMap] 里当前订阅源那一项上，
     * 切换订阅源后自然切换到该源自己的记忆，不需要额外清理。
     */
    var iptvChannelGroupHiddenList: Set<String>
        get() = iptvSourceGroupHiddenMap[currentNormalizedSourceUrl]?.toSet() ?: emptySet()
        set(value) {
            iptvSourceGroupHiddenMap =
                iptvSourceGroupHiddenMap + (currentNormalizedSourceUrl to value.toList())
        }

    /** 是否显示内置的「潮汕节目回放」分组（默认关闭，需要时自己在设置里开） */
    var iptvChaoshanSourceEnable: Boolean
        get() = SP.getBoolean(KEY.IPTV_CHAOSHAN_SOURCE_ENABLE.name, false)
        set(value) = SP.putBoolean(KEY.IPTV_CHAOSHAN_SOURCE_ENABLE.name, value)

    /** 当前订阅源（按内置源对齐后）的地址，作为按源记忆的键 */
    private val currentNormalizedSourceUrl: String
        get() = Constants.normalizeIptvSource(iptvSourceCurrent).url

    /** 混合模式 */
    var iptvHybridMode: IptvHybridMode
        get() = IptvHybridMode.fromValue(
            SP.getInt(KEY.IPTV_HYBRID_MODE.name, IptvHybridMode.DISABLE.value)
        )
        set(value) = SP.putInt(KEY.IPTV_HYBRID_MODE.name, value.value)

    /** ==================== 节目单 ==================== */
    /** 启用节目单 */
    var epgEnable: Boolean
        get() = SP.getBoolean(KEY.EPG_ENABLE.name, true)
        set(value) = SP.putBoolean(KEY.EPG_ENABLE.name, value)

    /** 当前节目单来源 */
    var epgSourceCurrent: EpgSource
        get() {
            val source = Json.decodeFromString<EpgSource>(
                SP.getString(KEY.EPG_SOURCE_CURRENT.name, "")
                    .ifBlank { Json.encodeToString(Constants.EPG_SOURCE_LIST.first()) }
            )

            // 老默认源指向服务器上预生成的 e.xml.gz，内容经常停在生成那一刻，
            // 凌晨会出现「当前时刻查不到节目」，这里迁移到实时生成的 e.xml
            return if (source.url == Constants.EPG_SOURCE_LEGACY_URL)
                Constants.EPG_SOURCE_LIST.first()
            else source
        }
        set(value) = SP.putString(KEY.EPG_SOURCE_CURRENT.name, Json.encodeToString(value))

    /** 节目单来源列表 */
    var epgSourceList: EpgSourceList
        get() = Json.decodeFromString(
            SP.getString(KEY.EPG_SOURCE_LIST.name, Json.encodeToString(EpgSourceList()))
        )
        set(value) = SP.putString(KEY.EPG_SOURCE_LIST.name, Json.encodeToString(value))

    /** 节目单刷新时间阈值（小时） */
    var epgRefreshTimeThreshold: Int
        get() = SP.getInt(KEY.EPG_REFRESH_TIME_THRESHOLD.name, Constants.EPG_REFRESH_TIME_THRESHOLD)
        set(value) = SP.putInt(KEY.EPG_REFRESH_TIME_THRESHOLD.name, value)

    /** 节目预约列表 */
    var epgChannelReserveList: EpgProgrammeReserveList
        get() = Json.decodeFromString(
            SP.getString(
                KEY.EPG_CHANNEL_RESERVE_LIST.name, Json.encodeToString(EpgProgrammeReserveList())
            )
        )
        set(value) = SP.putString(KEY.EPG_CHANNEL_RESERVE_LIST.name, Json.encodeToString(value))

    /** ==================== 界面 ==================== */
    /** 显示节目进度 */
    var uiShowEpgProgrammeProgress: Boolean
        get() = SP.getBoolean(KEY.UI_SHOW_EPG_PROGRAMME_PROGRESS.name, true)
        set(value) = SP.putBoolean(KEY.UI_SHOW_EPG_PROGRAMME_PROGRESS.name, value)

    /** 显示常驻节目进度 */
    var uiShowEpgProgrammePermanentProgress: Boolean
        get() = SP.getBoolean(KEY.UI_SHOW_EPG_PROGRAMME_PERMANENT_PROGRESS.name, false)
        set(value) = SP.putBoolean(KEY.UI_SHOW_EPG_PROGRAMME_PERMANENT_PROGRESS.name, value)

    /** 显示台标 */
    var uiShowChannelLogo: Boolean
        get() = SP.getBoolean(KEY.UI_SHOW_CHANNEL_LOGO.name, true)
        set(value) = SP.putBoolean(KEY.UI_SHOW_CHANNEL_LOGO.name, value)

    /** 使用经典选台界面 */
    /** 使用经典选台界面（三段式：左分组 / 中频道 / 右节目信息） */
    var uiUseClassicPanelScreen: Boolean
        get() = SP.getBoolean(KEY.UI_USE_CLASSIC_PANEL_SCREEN.name, true)
        set(value) = SP.putBoolean(KEY.UI_USE_CLASSIC_PANEL_SCREEN.name, value)

    /** 界面密度缩放比例 */
    var uiDensityScaleRatio: Float
        get() = SP.getFloat(KEY.UI_DENSITY_SCALE_RATIO.name, 0f)
        set(value) = SP.putFloat(KEY.UI_DENSITY_SCALE_RATIO.name, value)

    /** 界面字体缩放比例 */
    var uiFontScaleRatio: Float
        get() = SP.getFloat(KEY.UI_FONT_SCALE_RATIO.name, 1f)
        set(value) = SP.putFloat(KEY.UI_FONT_SCALE_RATIO.name, value)

    /** 时间显示模式 */
    var uiTimeShowMode: UiTimeShowMode
        get() = UiTimeShowMode.fromValue(
            SP.getInt(KEY.UI_TIME_SHOW_MODE.name, UiTimeShowMode.HIDDEN.value)
        )
        set(value) = SP.putInt(KEY.UI_TIME_SHOW_MODE.name, value.value)

    /** 焦点优化 */
    var uiFocusOptimize: Boolean
        get() = SP.getBoolean(KEY.UI_FOCUS_OPTIMIZE.name, true)
        set(value) = SP.putBoolean(KEY.UI_FOCUS_OPTIMIZE.name, value)

    /** 自动关闭界面延时 */
    var uiScreenAutoCloseDelay: Long
        get() =
            SP.getLong(KEY.UI_SCREEN_AUTO_CLOSE_DELAY.name, Constants.UI_SCREEN_AUTO_CLOSE_DELAY)
        set(value) = SP.putLong(KEY.UI_SCREEN_AUTO_CLOSE_DELAY.name, value)

    /** ==================== 更新 ==================== */
    /** 更新强提醒 */
    var updateForceRemind: Boolean
        get() = SP.getBoolean(KEY.UPDATE_FORCE_REMIND.name, false)
        set(value) = SP.putBoolean(KEY.UPDATE_FORCE_REMIND.name, value)

    /** 更新通道 */
    var updateChannel: String
        get() = SP.getString(KEY.UPDATE_CHANNEL.name, "stable")
        set(value) = SP.putString(KEY.UPDATE_CHANNEL.name, value)

    /** ==================== 播放器 ==================== */
    /** 播放器 自定义ua */
    var videoPlayerUserAgent: String
        get() = SP.getString(KEY.VIDEO_PLAYER_USER_AGENT.name, "").ifBlank {
            Constants.VIDEO_PLAYER_USER_AGENT
        }
        set(value) = SP.putString(KEY.VIDEO_PLAYER_USER_AGENT.name, value)

    /** 播放器 加载超时 */
    var videoPlayerLoadTimeout: Long
        get() = SP.getLong(KEY.VIDEO_PLAYER_LOAD_TIMEOUT.name, Constants.VIDEO_PLAYER_LOAD_TIMEOUT)
        set(value) = SP.putLong(KEY.VIDEO_PLAYER_LOAD_TIMEOUT.name, value)

    /** 播放器 显示模式（默认 16:9，铺满绝大多数电视与显示器） */
    var videoPlayerDisplayMode: VideoPlayerDisplayMode
        get() = VideoPlayerDisplayMode.fromValue(
            SP.getInt(
                KEY.VIDEO_PLAYER_DISPLAY_MODE.name,
                VideoPlayerDisplayMode.SIXTEEN_NINE.value,
            )
        )
        set(value) = SP.putInt(KEY.VIDEO_PLAYER_DISPLAY_MODE.name, value.value)

    /** 各频道单独记住的显示比例（频道名 → 模式值） */
    var videoPlayerDisplayModeChannelMap: Map<String, Int>
        get() = decodeIntMap(
            SP.getString(KEY.VIDEO_PLAYER_DISPLAY_MODE_CHANNEL_MAP.name, "{}")
        )
        set(value) = SP.putString(
            KEY.VIDEO_PLAYER_DISPLAY_MODE_CHANNEL_MAP.name, Json.encodeToString(value)
        )

    /**
     * 当前频道实际生效的显示比例
     *
     * 全局默认（默认 16:9）铺底，只有被单独设过的频道才用自己记住的比例。
     * 换台、重启、换订阅源都按这个规则解析。
     */
    fun resolveVideoPlayerDisplayMode(channel: Channel): VideoPlayerDisplayMode {
        videoPlayerDisplayModeChannelMap[channel.name]?.let {
            return VideoPlayerDisplayMode.fromValue(it)
        }
        return videoPlayerDisplayMode
    }

    /** 记住某个频道单独的显示比例（只影响这个频道） */
    fun rememberVideoPlayerDisplayMode(mode: VideoPlayerDisplayMode, channel: Channel) {
        videoPlayerDisplayModeChannelMap =
            videoPlayerDisplayModeChannelMap + (channel.name to mode.value)
    }

    /** 忘掉某个频道的单独设置，回到全局默认比例 */
    fun forgetVideoPlayerDisplayMode(channel: Channel) {
        if (!videoPlayerDisplayModeChannelMap.containsKey(channel.name)) return
        videoPlayerDisplayModeChannelMap = videoPlayerDisplayModeChannelMap - channel.name
    }

    private fun decodeIntMap(raw: String): Map<String, Int> =
        runCatching { Json.decodeFromString<Map<String, Int>>(raw) }.getOrElse { emptyMap() }

    private fun decodeStringListMap(raw: String): Map<String, List<String>> =
        runCatching { Json.decodeFromString<Map<String, List<String>>>(raw) }
            .getOrElse { emptyMap() }

    /** 播放器 强制音频软解 */
    var videoPlayerForceAudioSoftDecode: Boolean
        get() = SP.getBoolean(KEY.VIDEO_PLAYER_FORCE_AUDIO_SOFT_DECODE.name, false)
        set(value) = SP.putBoolean(KEY.VIDEO_PLAYER_FORCE_AUDIO_SOFT_DECODE.name, value)

    /** 播放器 渲染方式 */
    var videoPlayerRenderMode: VideoPlayerRenderMode
        get() = VideoPlayerRenderMode.fromValue(
            SP.getInt(KEY.VIDEO_PLAYER_RENDER_MODE.name, VideoPlayerRenderMode.SURFACE_VIEW.value)
        )
        set(value) = SP.putInt(KEY.VIDEO_PLAYER_RENDER_MODE.name, value.value)

    /** 播放器 停止上一媒体项 */
    var videoPlayerStopPreviousMediaItem: Boolean
        get() = SP.getBoolean(KEY.VIDEO_PLAYER_STOP_PREVIOUS_MEDIA_ITEM.name, true)
        set(value) = SP.putBoolean(KEY.VIDEO_PLAYER_STOP_PREVIOUS_MEDIA_ITEM.name, value)

    /** 播放器 跳过同一VSync渲染多帧 */
    var videoPlayerSkipMultipleFramesOnSameVSync: Boolean
        get() = SP.getBoolean(KEY.VIDEO_PLAYER_SKIP_MULTIPLE_FRAMES_ON_SAME_VSYNC.name, false)
        set(value) = SP.putBoolean(KEY.VIDEO_PLAYER_SKIP_MULTIPLE_FRAMES_ON_SAME_VSYNC.name, value)

    enum class UiTimeShowMode(val value: Int) {
        /** 隐藏 */
        HIDDEN(0),

        /** 常显 */
        ALWAYS(1),

        /** 整点 */
        EVERY_HOUR(2),

        /** 半点 */
        HALF_HOUR(3);

        companion object {
            fun fromValue(value: Int): UiTimeShowMode {
                return entries.firstOrNull { it.value == value } ?: ALWAYS
            }
        }
    }

    enum class IptvHybridMode(val value: Int) {
        /** 禁用 */
        DISABLE(0),

        /** 直播源优先 */
        IPTV_FIRST(1),

        /** 混合优先 */
        HYBRID_FIRST(2);

        companion object {
            fun fromValue(value: Int): IptvHybridMode {
                return entries.firstOrNull { it.value == value } ?: DISABLE
            }
        }
    }

    enum class VideoPlayerRenderMode(val value: Int, val label: String) {
        /** SurfaceView */
        SURFACE_VIEW(0, "SurfaceView"),

        /** TextureView */
        TEXTURE_VIEW(1, "TextureView");

        companion object {
            fun fromValue(value: Int): VideoPlayerRenderMode {
                return entries.firstOrNull { it.value == value } ?: SURFACE_VIEW
            }
        }
    }

    /**
     * 升级后的一次性配置迁移
     *
     * 需要给老用户「刷上新版本默认值」时，把 [CURRENT_CONFIG_VERSION] +1 并在下面补对应逻辑。
     * 注意：SharedPreferences 分不清「没设置」和「设置为默认值」，所以新默认值一般直接改
     * getter 的默认值即可——没设置过的用户会自动跟随，显式设置过的保持不变。
     */
    fun migrateIfNeeded() {
        val savedVersion = SP.getInt(KEY.APP_CONFIG_VERSION.name, 0)
        if (savedVersion >= CURRENT_CONFIG_VERSION) return

        if (savedVersion < 2) {
            // v2（1.4）：分组隐藏从「全局一份」改成「按订阅源记忆」。
            // 老配置里的全局隐藏列表迁移到当时的订阅源名下，避免设定丢失。
            val legacy = SP.getStringSet(KEY.IPTV_CHANNEL_GROUP_HIDDEN_LIST.name, emptySet())
            if (legacy.isNotEmpty()) {
                val sourceUrl = runCatching {
                    Constants.normalizeIptvSource(
                        Json.decodeFromString<IptvSource>(
                            SP.getString(KEY.IPTV_SOURCE_CURRENT.name, "")
                                .ifBlank { Json.encodeToString(Constants.IPTV_SOURCE_LIST.first()) }
                        )
                    ).url
                }.getOrElse { Constants.IPTV_SOURCE_LIST.first().url }

                iptvSourceGroupHiddenMap = iptvSourceGroupHiddenMap + (sourceUrl to legacy.toList())
            }
        }

        // v1（1.3）：三段式选台与 16:9 显示改为默认值（getter 默认值已更新，
        // 老配置里没有这两个键的用户自动跟随新默认，无需写库）

        if (savedVersion < 3) {
            // v3（1.5）：潮汕节目回放改为默认关闭；订阅地址已内置成一个可切换的订阅源，
            // 需要时自己在设置里开（或直接在「换源」里切到该源）
            iptvChaoshanSourceEnable = false
        }

        SP.putInt(KEY.APP_CONFIG_VERSION.name, CURRENT_CONFIG_VERSION)
    }

    /** 当前配置版本，每次需要把新默认值刷到老配置上时 +1 */
    private const val CURRENT_CONFIG_VERSION = 3
}