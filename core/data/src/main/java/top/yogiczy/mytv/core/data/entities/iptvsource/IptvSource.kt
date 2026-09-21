package top.yogiczy.mytv.core.data.entities.iptvsource

import kotlinx.serialization.Serializable
import top.yogiczy.mytv.core.data.utils.Globals

/**
 *  直播源
 */
@Serializable
data class IptvSource(
    /**
     * 名称
     */
    val name: String = "",

    /**
     * 链接
     */
    val url: String = "",

    /**
     * 是否本地
     */
    val isLocal: Boolean = false,

    /**
     * 是否参与「线路测速排序」
     *
     * 只有内置的默认直播源打开这个开关：它每个频道挂着十几条线路，测速重排能明显改善播放体验。
     *
     * 斗鱼/虎牙/YY 这类订阅每个频道只有一条线路，测速没有意义，
     * 而频道数量动辄上千，探测会长时间占满带宽、拖累正在播放的画面，所以它们固定为 false。
     */
    val lineSpeedSort: Boolean = false,
) {
    companion object {
        fun IptvSource.needExternalStoragePermission(): Boolean {
            return this.isLocal && !this.url.startsWith(Globals.cacheDir.path)
        }
    }
}