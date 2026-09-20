package top.yogiczy.mytv.core.data.repositories.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
 * 结果会缓存到本地（见 [SPEED_CACHE_TIME]），之后启动直接套用，不再阻塞等待。
 */
class ChannelLineSpeedRepository(
    source: IptvSource,
) : FileCacheRepository("iptv-speed-${source.url.hashCode().toUInt().toString(16)}.json") {
    private val log = Logger.create(javaClass.simpleName)

    private val client = OkHttpClient.Builder()
        .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(PROBE_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

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
     * @param urls 全部线路地址
     * @param force 为 true 时忽略缓存有效期，强制重测
     */
    suspend fun refresh(urls: List<String>, force: Boolean = false): ChannelLineSpeedTable {
        val cached = load()
        val expired = System.currentTimeMillis() - cached.updatedAt >= SPEED_CACHE_TIME
        if (!force && !expired) return cached

        val targets = urls.filter { it.isNotBlank() }.distinct()
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
     * 并发探测各线路的响应耗时
     */
    private suspend fun measure(urls: List<String>): Map<String, Int> = coroutineScope {
        val semaphore = Semaphore(PROBE_CONCURRENCY)

        urls.map { url ->
            async(Dispatchers.IO) {
                semaphore.withPermit { url to probe(url) }
            }
        }.awaitAll().toMap()
    }

    /**
     * 探测单条线路
     *
     * 只发一个很小的 Range 请求并只等响应头，不读正文，避免真的把流拉下来。
     */
    private fun probe(url: String): Int {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return RANK_UNKNOWN

        return try {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-2047")
                .header("Connection", "close")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) return RANK_FAILED

                val cost =
                    (response.receivedResponseAtMillis - response.sentRequestAtMillis).toInt()
                if (cost < 0) RANK_UNKNOWN else cost
            }
        } catch (ex: Exception) {
            RANK_FAILED
        }
    }

    companion object {
        /** 单条线路的探测超时 */
        private const val PROBE_TIMEOUT_MS = 2500L

        /** 并发探测数 */
        private const val PROBE_CONCURRENCY = 24

        /** 测速结果有效期 */
        private const val SPEED_CACHE_TIME = 1000L * 60 * 60 * 6

        /** 无法测速的线路（非 http/https），给一个靠后的排名 */
        private const val RANK_UNKNOWN = 3000

        /** 探测失败（打不开），排到最后 */
        private const val RANK_FAILED = 60000
    }
}
