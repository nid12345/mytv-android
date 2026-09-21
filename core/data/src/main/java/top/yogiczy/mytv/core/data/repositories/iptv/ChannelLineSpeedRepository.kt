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
     * 线路地址 → 响应耗时（毫秒）
     *
     * - `>= 0`：探到了，数值就是耗时。HLS 线路记的是「读完整个清单」的耗时，
     *   清单虽小，但它每隔一个分段时长就要重刷一次，拉得慢就会让直播掉出窗口。
     * - `-1`：打不开（超时/4xx/5xx）。
     * - `-2`：无法探测（非 http/https，比如 rtmp）。
     */
    val latency: Map<String, Int> = emptyMap(),
    /**
     * 线路地址 → 实测下行速率（kbps）
     *
     * 只对每个频道响应最快的前几条实测（见 [THROUGHPUT_TOP_PER_CHANNEL]）。
     * `-1` 表示没测到有效数据（可能这条线路只认特定 UA），这种情况不做惩罚，
     * 该线路仍按响应速度参与排序。
     */
    val throughput: Map<String, Int> = emptyMap(),
)

/**
 * 直播源线路测速
 *
 * 一个频道往往挂着十几条线路，源里的先后顺序并不代表播放效果。分两步挑线路：
 *
 * 1. **廉价探测（覆盖全部线路）**：普通线路只「握手 + 取响应头」；
 *    HLS 线路改为把整份清单读下来并计时——第三方转发站的清单经常要 2~3 秒才回，
 *    而直播 HLS 每隔一个分段时长（常见 10 秒）就要重刷一次清单，这类线路必须先被识别出来。
 * 2. **实测吞吐（每频道响应最快的前几条）**：真拉一小段媒体数据（时长与字节数双封顶），
 *    算出真实下行速率。只比响应头会把「响应快、但拉不动大分段」的线路误判成最好的线路，
 *    这正是「打开就卡」的常见原因。
 *
 * 排序优先级：实测速率高的 → 响应快的 → 未知 → 打不开。
 *
 * 探测刻意做得很克制：并发小、批间停顿、单条取流时长极短，避免把带宽占满打扰正在播放的流。
 * 结果会缓存到本地（见 [SPEED_CACHE_TIME]），之后启动直接套用，不再重复探测。
 */
class ChannelLineSpeedRepository(
    source: IptvSource,
) : FileCacheRepository("iptv-speed-v2-${source.url.hashCode().toUInt().toString(16)}.json") {
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
     * @param channelUrlGroups 每个频道的线路列表（用于挑出「每频道最快的前几条」做实测吞吐）
     * @param force 为 true 时忽略缓存有效期，强制重测
     * @param excludeUrls 不参与探测的线路（一般传当前正在播放的频道，它已经在播，无需再测）
     */
    suspend fun refresh(
        channelUrlGroups: List<List<String>>,
        force: Boolean = false,
        excludeUrls: Set<String> = emptySet(),
    ): ChannelLineSpeedTable {
        val cached = load()
        val expired = System.currentTimeMillis() - cached.updatedAt >= SPEED_CACHE_TIME
        if (!force && !expired) return cached

        val targets = channelUrlGroups.flatten()
            .filter { it.isNotBlank() }
            .distinct()
            .filter { it !in excludeUrls }
        if (targets.isEmpty()) return cached

        val startAt = System.currentTimeMillis()

        // 阶段一：全部线路的响应耗时（HLS 记清单耗时）
        val probed = measure(targets)

        // 阶段二：每个频道挑响应最快的前几条，实测真实下行速率
        val candidates = pickThroughputCandidates(channelUrlGroups, probed, excludeUrls)
        val throughput = measureThroughput(candidates)

        val table = ChannelLineSpeedTable(
            updatedAt = System.currentTimeMillis(),
            latency = cached.latency + probed,
            throughput = cached.throughput + throughput,
        )

        runCatching { setCacheData(Json.encodeToString(table)) }
            .onFailure { log.e("写入线路测速缓存失败", it) }

        log.i(
            "线路测速完成：响应 ${probed.size}/${targets.size} 条（可用 " +
                    "${probed.count { it.value >= 0 }} 条），" +
                    "实测吞吐 ${throughput.count { it.value > 0 }}/${candidates.size} 条，" +
                    "耗时 ${System.currentTimeMillis() - startAt}ms"
        )

        return table
    }

    /**
     * 按测速结果给每个频道的线路排序
     *
     * 实测速率高的排最前，其次按响应速度，未知排后，打不开排最后；
     * 同分时保持源里的原始顺序（稳定排序）。
     */
    fun sortByCache(
        channelGroupList: ChannelGroupList,
        table: ChannelLineSpeedTable,
    ): ChannelGroupList {
        if (table.latency.isEmpty() && table.throughput.isEmpty()) return channelGroupList

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

    /**
     * 单条线路的排序权重（越小越好）
     *
     * - 有实测速率：1 .. RANK_LATENCY_BASE-1（越快越小）
     * - 只测了响应：RANK_LATENCY_BASE .. +999（越快越小）
     * - 未知（没测过 / 非 http）：RANK_UNKNOWN
     * - 打不开：RANK_FAILED
     */
    private fun rank(table: ChannelLineSpeedTable, url: String): Int {
        val speed = table.throughput[url]
        if (speed != null && speed > 0) {
            return (RANK_LATENCY_BASE - speed).coerceIn(1, RANK_LATENCY_BASE - 1)
        }

        return when (val latency = table.latency[url]) {
            null -> RANK_UNKNOWN
            RANK_LATENCY_UNMEASURABLE -> RANK_UNKNOWN
            RANK_LATENCY_FAILED -> RANK_FAILED
            in Int.MIN_VALUE..-3 -> RANK_FAILED
            else -> RANK_LATENCY_BASE + latency.coerceIn(0, 999)
        }
    }

    /**
     * 挑出每个频道里值得做实测吞吐的线路
     *
     * 只取响应最快的前 [THROUGHPUT_TOP_PER_CHANNEL] 条：一个频道十几条线路全测一遍
     * 要多拉几十 MB 流量，而排在后面的线路本来也不会被选中。
     */
    private fun pickThroughputCandidates(
        channelUrlGroups: List<List<String>>,
        probed: Map<String, Int>,
        excludeUrls: Set<String>,
    ): List<String> {
        val picked = LinkedHashSet<String>()

        channelUrlGroups.forEach { urls ->
            // 只有一个线路的频道没有比较意义，别浪费流量
            if (urls.size <= 1) return@forEach

            urls.filter { it !in excludeUrls }
                .filter { (probed[it] ?: -1) >= 0 }
                .sortedBy { probed[it] }
                .take(THROUGHPUT_TOP_PER_CHANNEL)
                .forEach { picked.add(it) }
        }

        return picked.toList()
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
     * 分批实测各线路的真实下行速率
     */
    private suspend fun measureThroughput(urls: List<String>): Map<String, Int> {
        if (urls.isEmpty()) return emptyMap()

        val result = LinkedHashMap<String, Int>()

        urls.chunked(THROUGHPUT_CONCURRENCY).forEachIndexed { batchIdx, batch ->
            val measured = coroutineScope {
                batch.map { url ->
                    async(Dispatchers.IO) {
                        // HLS 线路直接拉它最新的一个分段，比拉清单更能反映真实供流能力
                        val target =
                            if (isHlsUrl(url)) resolveHlsSegment(url) ?: url else url
                        url to throughput(target)
                    }
                }.awaitAll()
            }
            measured.forEach { (url, kbps) -> result[url] = kbps }

            if (batchIdx != urls.lastIndex) delay(BATCH_PAUSE_MS)
        }

        return result
    }

    /**
     * 探测单条线路的响应耗时
     *
     * 普通线路只发一个很小的 Range 请求并只等响应头，不读正文，避免真的把流拉下来；
     * HLS 线路则把整份清单读完——清单本身很小（几百字节），
     * 但它每隔一个分段时长就要重刷一次，它的耗时决定了直播会不会掉出窗口。
     * 少数服务器不支持 Range（回 416），这种情况再补一次不带 Range 的请求，
     * 免得把本来能播的线路误判成打不开。
     *
     * @return 耗时毫秒；[RANK_LATENCY_FAILED] 打不开；[RANK_LATENCY_UNMEASURABLE] 无法探测
     */
    private fun probe(url: String): Int {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return RANK_LATENCY_UNMEASURABLE
        }

        if (isHlsUrl(url)) {
            val playlistCost = playlistProbe(url)
            if (playlistCost != null) return playlistCost
        }

        val ranged = request(url, withRange = true)
        if (ranged != null) return ranged

        return request(url, withRange = false) ?: RANK_LATENCY_FAILED
    }

    /**
     * HLS 清单探测：读完整份清单并计时
     *
     * @return 耗时毫秒；读不到返回 null（交给普通探测再试一次）
     */
    private fun playlistProbe(url: String): Int? {
        return try {
            val startedAt = System.nanoTime()
            CLIENT.newCall(Request.Builder().url(url).header("Connection", "close").build())
                .execute().use { response ->
                    if (!response.isSuccessful) return null

                    response.peekBody(MAX_PLAYLIST_BYTES.toLong()).string()
                    val cost = ((System.nanoTime() - startedAt) / 1_000_000).toInt()
                    if (cost < 0) RANK_LATENCY_UNMEASURABLE else cost
                }
        } catch (ex: Exception) {
            null
        }
    }

    /**
     * 单次探测（只看响应头）
     *
     * @return 拿到响应头时为耗时毫秒；请求失败返回 null
     */
    private fun request(url: String, withRange: Boolean): Int? {
        return try {
            val builder = Request.Builder()
                .url(url)
                .header("Connection", "close")
            if (withRange) builder.header("Range", "bytes=0-2047")

            CLIENT.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) return null

                val cost =
                    (response.receivedResponseAtMillis - response.sentRequestAtMillis).toInt()
                if (cost < 0) RANK_LATENCY_UNMEASURABLE else cost
            }
        } catch (ex: Exception) {
            null
        }
    }

    /**
     * 从 HLS 地址解析出一个可直接拉取的分段地址
     *
     * 取清单里**最后一个**分段：直播清单只保留最近几个分段，第一个往往已经过期。
     * 遇到主清单（含 #EXT-X-STREAM-INF）先下沉一层到变体清单再取分段。
     */
    private fun resolveHlsSegment(url: String, depth: Int = 1): String? {
        val playlist = fetchPlaylist(url) ?: return null
        val uris = playlist.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        if (uris.isEmpty()) return null

        return if (playlist.contains("#EXT-X-STREAM-INF")) {
            if (depth <= 0) null else resolveHlsSegment(absoluteUrl(url, uris.first()), depth - 1)
        } else {
            absoluteUrl(url, uris.last())
        }
    }

    /** 读取一份 HLS 清单（读不到返回 null） */
    private fun fetchPlaylist(url: String): String? {
        return try {
            CLIENT.newCall(Request.Builder().url(url).header("Connection", "close").build())
                .execute().use { response ->
                    if (!response.isSuccessful) null
                    else response.peekBody(MAX_PLAYLIST_BYTES.toLong()).string()
                }
        } catch (ex: Exception) {
            null
        }
    }

    /** 把清单里的相对地址补成绝对地址 */
    private fun absoluteUrl(playlistUrl: String, uri: String): String =
        if (uri.startsWith("http://") || uri.startsWith("https://")) uri
        else playlistUrl.substringBeforeLast('/', playlistUrl) + "/" + uri

    /**
     * 实测一条媒体地址的下行速率（kbps）
     *
     * 限定 [THROUGHPUT_DURATION_MS] 毫秒或 [THROUGHPUT_MAX_BYTES] 字节，先到为准：
     * 既要把「响应快但拉不动」的线路暴露出来，又不能真的把整段流下完。
     *
     * @return 速率 kbps；取不到足够数据时返回 -1
     */
    private fun throughput(url: String): Int {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return -1

        return try {
            val startedAt = System.nanoTime()
            var total = 0L

            CLIENT.newCall(Request.Builder().url(url).header("Connection", "close").build())
                .execute().use { response ->
                    if (!response.isSuccessful) return -1

                    val body = response.body ?: return -1
                    val source = body.source()
                    val buffer = ByteArray(READ_BUFFER_SIZE)
                    while (total < THROUGHPUT_MAX_BYTES) {
                        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                        if (elapsedMs >= THROUGHPUT_DURATION_MS) break

                        val read = source.read(buffer)
                        if (read <= 0) break
                        total += read
                    }
                }

            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            if (total < MIN_THROUGHPUT_BYTES || elapsedMs <= 0) -1
            else (total * 8 / elapsedMs).toInt()
        } catch (ex: Exception) {
            -1
        }
    }

    private fun isHlsUrl(url: String): Boolean =
        url.substringBefore('?').lowercase().endsWith(".m3u8")

    companion object {
        /** 单条线路的探测超时 */
        private const val PROBE_TIMEOUT_MS = 1500L

        /** 实测吞吐时单条线路最长取流时间 */
        private const val THROUGHPUT_DURATION_MS = 250L

        /** 实测吞吐时单条线路最多拉取的字节数 */
        private const val THROUGHPUT_MAX_BYTES = 192L * 1024

        /** 少于这个字节数就认为没测到有效速率 */
        private const val MIN_THROUGHPUT_BYTES = 16L * 1024

        /** 读缓冲区大小 */
        private const val READ_BUFFER_SIZE = 16 * 1024

        /** 清单最多读这么多字节（正常几百字节，主清单也够） */
        private const val MAX_PLAYLIST_BYTES = 32 * 1024

        /** 每个频道参与实测吞吐的线路条数（按响应速度取前几条） */
        private const val THROUGHPUT_TOP_PER_CHANNEL = 2

        /**
         * 每批并发数
         *
         * 数值越小越不打扰正在播放的流，代价是整体测速更慢。
         * 测速全程在后台跑、结果还缓存 12 小时，所以宁可慢一点。
         */
        private const val PROBE_CONCURRENCY = 4

        /** 实测吞吐的并发数（要真拉流量，比响应探测更克制） */
        private const val THROUGHPUT_CONCURRENCY = 2

        /** 批与批之间的停顿，主动把带宽让出来 */
        private const val BATCH_PAUSE_MS = 200L

        /** 测速结果有效期 */
        private const val SPEED_CACHE_TIME = 1000L * 60 * 60 * 12

        /** 线路测速结果里：打不开 */
        private const val RANK_LATENCY_FAILED = -1

        /** 线路测速结果里：无法探测（非 http/https） */
        private const val RANK_LATENCY_UNMEASURABLE = -2

        /** 排序权重：未知（没测过 / 非 http） */
        private const val RANK_UNKNOWN = 3000

        /** 排序权重：打不开 */
        private const val RANK_FAILED = 60000

        /**
         * 只测了响应速度的线路的排位基准（2000 起）
         *
         * 实测速率换算出的排位落在 1..1999，所以「有实测速率」一定优先于「只测了响应」。
         */
        private const val RANK_LATENCY_BASE = 2000

        /**
         * 所有测速共用同一个客户端
         *
         * 复用连接池，避免每条线路都重新握手；每个实例单独建客户端的话，
         * 线程与连接会成倍增长。
         */
        private val CLIENT: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(THROUGHPUT_DURATION_MS + PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .callTimeout(THROUGHPUT_DURATION_MS + PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .build()
        }
    }
}
