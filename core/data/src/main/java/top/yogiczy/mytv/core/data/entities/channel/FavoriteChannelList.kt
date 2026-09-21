package top.yogiczy.mytv.core.data.entities.channel

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * 收藏的频道快照
 *
 * 收藏不能只记频道名：不同订阅源的频道互不重叠，只记名字的话，
 * 一切换订阅源收藏列表就空了。这里把频道的线路地址一起存下来，
 * 于是收藏可以跨订阅源保留——只要自己不取消，换到任何源都还能看到、还能播。
 */
@Serializable
data class FavoriteChannel(
    /**
     * 频道名称
     */
    val name: String = "",

    /**
     * 节目单名称，用于查询节目单
     */
    val epgName: String = "",

    /**
     * 播放地址（多个为多线路）
     */
    val urlList: List<String> = emptyList(),

    /**
     * 台标
     */
    val logo: String? = null,
) {
    /**
     * 快照里没有可用线路时无法播放（一般是旧版本只存了频道名）
     */
    fun toChannelOrNull(): Channel? =
        if (name.isBlank() || urlList.isEmpty()) null
        else Channel(name = name, epgName = epgName, urlList = urlList, logo = logo)

    companion object {
        fun Channel.toFavoriteChannel(): FavoriteChannel =
            FavoriteChannel(name = name, epgName = epgName, urlList = urlList, logo = logo)
    }
}

/**
 * 收藏频道列表
 *
 * 顺序就是收藏顺序，跨订阅源保持稳定。
 */
@Serializable
@Immutable
data class FavoriteChannelList(
    val value: List<FavoriteChannel> = emptyList(),
) : List<FavoriteChannel> by value {
    /**
     * 转成可以播放的频道列表
     *
     * 优先用当前订阅源里的同频道（线路最新、还能顺便享受线路排序），
     * 当前源里没有的（收藏自其它订阅源）就用收藏时存下的快照。
     *
     * @param currentChannelList 当前订阅源的全部频道
     */
    fun resolve(currentChannelList: List<Channel>): ChannelList = ChannelList(
        mapNotNull { favorite ->
            currentChannelList.firstOrNull { it.name == favorite.name }
                ?: favorite.toChannelOrNull()
        }
    )
}
