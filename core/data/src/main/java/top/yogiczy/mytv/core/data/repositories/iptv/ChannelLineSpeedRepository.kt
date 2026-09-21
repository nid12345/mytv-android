package top.yogiczy.mytv.core.data.repositories.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.repositories.FileCacheRepository
import top.yogiczy.mytv.core.data.utils.Logger
import java.util.concurrent.TimeUnit

/**
 * 线路测速结果
 */
@Serializable
data class ChannelLineSpeedTable(
    /** 上次测速时间 */
    val updatedAt: Long = 0L,
    /**
     * 线路地址 → 连接耗时（毫秒）
     *
     * 探测失败记 -1，用于把打不开的线路排到最后。
     */
    val latency: Map<String, Int> = emptyMap(),
)

/**
 * 直播源线路测速
 *
 * 一个频道往往挂着十几条线路，源里的先后顺序并不代表播放效果。
 * 这里对每条线路做一次轻量的「连上并拿到响应头」探测，按耗时从快到慢重排，
 * 让排在第一位的线路尽量是又快又不卡的。
 *
 * 探测刻意做得很克制：并发很小、批与批之间主动停顿，避免把带宽占满——
 * 之前的版本一次开 24 条并发探测，测速期间正在播放的画面会被挤到卡顿甚至超时，
 * 反而像是「播放器坏了」。现在宁可多花一点时间，也不打扰正在播放的流。
 *
 * 结果会缓存到本地（见 [SPEED_CACHE_TIME]），之后启动直接套用，不再重复探测。
 */
class ChannelLineSpeedRepository(
    source: IptvSource,
) : FileCacheRepository("iptv-speed-${source.url.hashCode().toUInt().toString(16)}.json") {
    private val log = Logger.create(javaClass.simpleName)

    /**
     * 读取本地测速结果
     */
    suspend fun load(): ChannelLineSpeedTable {
        val json = runCatching { getCacheData() }.getOrNull()
        if (json.isNullOrBlank()) return ChannelLineSpeedTable()

        return runCatching { Json.decodeFromString<ChannelLineSpeedTable>(json) }
            .getOrElse {
                log.e("读取线路测速缓存失败", it)
                ChannelLineSpeedTable()
            }
    }

    /**
     * 给还没测过的线路测速并落盘
     *
     * @param urls 需要探测的线路地址
     * @param force 为 true 时忽略缓存有效期，强制重测
     * @param excludeUrls 不参与探测的线路（一般传当前正在播放的频道，它已经在播，无需再测）
     */
    suspend fun refresh(
        urls: List<String>,
        force: Boolean = false,
        excludeUrls: Set<String> = emptySet(),
    ): ChannelLineSpeedTable {
        val cached = load()
        val expired = System.currentTimeMillis() - cached.updatedAt >= SPEED_CACHE_TIME
        if (!force && !expired) return cached

        val targets = urls.filter { it.isNotBlank() }.distinct().filter { it !in excludeUrls }
        if (targets.isEmpty()) return cached

        val startAt = System.currentTimeMillis()
        val measured = measure(targets)
        val table = ChannelLineSpeedTable(
            updatedAt = System.currentTimeMillis(),
            latency = cached.latency + measured,
        )

        runCatching { setCacheData(Json.encodeToString(table)) }
            .onFailure { log.e("写入线路测速缓存失败", it) }

        log.i(
            "线路测速完成：${measured.size}/${targets.size} 条，" +
                    "可用 ${measured.count { it.value >= 0 }} 条，" +
                    "耗时 ${System.currentTimeMillis() - startAt}ms"
        )

        return table
    }

    /**
     * 按测速结果给每个频道的线路排序
     *
     * 没测过的线路排在已测线路之后，打不开的排最后；
     * 同分时保持源里的原始顺序（稳定排序）。
     */
    fun sortByCache(
        channelGroupList: ChannelGroupList,
        table: ChannelLineSpeedTable,
    ): ChannelGroupList {
        if (table.latency.isEmpty()) return channelGroupList

        return ChannelGroupList(channelGroupList.map { group ->
            group.copy(channelList = ChannelList(group.channelList.map { channel ->
                if (channel.urlList.size <= 1) channel
                else channel.copy(
                    urlList = channel.urlList
                        .withIndex()
                        .sortedWith(compareBy({ rank(table, it.value) }, { it.index }))
                        .map { it.value }
                )
            }))
        })
    }

    private fun rank(table: ChannelLineSpeedTable, url: String): Int =
        when (val latency = table.latency[url]) {
            null -> RANK_UNKNOWN
            in Int.MIN_VALUE..-1 -> RANK_FAILED
            else -> latency
        }

    /**
     * 分批探测各线路的响应耗时
     *
     * 每批 [PROBE_CONCURRENCY] 条，批与批之间停顿 [BATCH_PAUSE_MS]，
     * 把瞬时连接数压到最低，给正在播放的流留出带宽。
     */
    private suspend fun measure(urls: List<String>): Map<String, Int> {
        val result = LinkedHashMap<String, Int>()

        urls.chunked(PROBE_CONCURRENCY).forEachIndexed { batchIdx, batch ->
            val measured = coroutineScope {
                batch.map { url ->
                    async(Dispatchers.IO) { url to probe(url) }
                }.awaitAll()
            }
            measured.forEach { (url, latency) -> result[url] = latency }

            if (batchIdx != urls.lastIndex) delay(BATCH_PAUSE_MS)
        }

        return result
    }

    /**
     * 探测单条线路
     *
     * 只发一个很小的 Range 请求并只等响应头，不读正文，避免真的把流拉下来。
     * 少数服务器不支持 Range（回 416），这种情况再补一次不带 Range 的请求，
     * 免得把本来能播的线路误判成打不开。
     */
    private fun probe(url: String): Int {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return RANK_UNKNOWN

        val ranged = request(url, withRange = true)
        if (ranged.latency != null) return ranged.latency
        if (!ranged.rangeRejected) return RANK_FAILED

        return request(url, withRange = false).latency ?: RANK_FAILED
    }

    /**
     * 单次探测
     *
     * @return 拿到响应头时 [latency] 为耗时毫秒；否则为 null，
     *         并可能带 [rangeRejected] 表示失败原因是「不支持 Range」。
     */
    private fun request(url: String, withRange: Boolean): ProbeResult {
        return try {
            val builder = Request.Builder()
                .url(url)
                .header("Connection", "close")
            if (withRange) builder.header("Range", "bytes=0-2047")

            CLIENT.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return ProbeResult(latency = null, rangeRejected = response.code == 416)
                }

                val cost =
                    (response.receivedResponseAtMillis - response.sentRequestAtMillis).toInt()
                ProbeResult(latency = if (cost < 0) RANK_UNKNOWN else cost)
            }
        } catch (ex: Exception) {
            ProbeResult(latency = null)
        }
    }

    private data class ProbeResult(
        val latency: Int? = null,
        val rangeRejected: Boolean = false,
    )

    companion object {
        /** 单条线路的探测超时 */
        private const val PROBE_TIMEOUT_MS = 1500L

        /**
         * 每批并发数
         *
         * 数值越小越不打扰正在播放的流，代价是整体测速更慢。
         * 测速全程在后台跑、结果还缓存 12 小时，所以宁可慢一点。
         */
        private const val PROBE_CONCURRENCY = 4

        /** 批与批之间的停顿，主动把带宽让出来 */
        private const val BATCH_PAUSE_MS = 200L

        /** 测速结果有效期 */
        private const val SPEED_CACHE_TIME = 1000L * 60 * 60 * 12

        /** 无法测速的线路（非 http/https），给一个靠后的排名 */
        private const val RANK_UNKNOWN = 3000

        /** 探测失败（打不开），排到最后 */
        private const val RANK_FAILED = 60000

        /**
         * 所有测速共用同一个客户端
         *
         * 复用连接池，避免每条线路都重新握手；每个实例单独建客户端的话，
         * 线程与连接会成倍增长。
         */
        private val CLIENT: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .callTimeout(PROBE_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .build()
        }
    }
}
