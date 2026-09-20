package top.yogiczy.mytv.core.data.repositories.epg

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import top.yogiczy.mytv.core.data.entities.epg.Epg
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeList
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.network.await
import top.yogiczy.mytv.core.data.repositories.FileCacheRepository
import top.yogiczy.mytv.core.data.repositories.epg.fetcher.EpgFetcher
import top.yogiczy.mytv.core.data.utils.ChannelName
import top.yogiczy.mytv.core.data.utils.Logger
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 节目单获取
 *
 * 支持「主源 + 补齐源」：
 * 1. 主源里「当前时刻查不到正在播的节目」的频道，会依次从补齐源取同名频道的节目补上，
 *    这样即使某个源缺 CCTV1、翡翠台、海南卫视 之类的频道，界面上也不会缺节目单；
 * 2. 主源整体不可用（例如源站给的是未更新的旧文件）时，由补齐源兜底。
 *
 * 另外 [getEpgList] **不会**再因为「未到刷新时间点」返回空节目单——
 * 老逻辑在凌晨 0 点到刷新阈值之间会让整个节目单凭空消失。
 */
class EpgRepository(
    private val source: EpgSource,
    private val fallbackSources: List<EpgSource> = emptyList(),
) {
    private val log = Logger.create(javaClass.simpleName)

    /** 参与聚合的节目单来源（主源排第一，按 url 去重） */
    private val sources: List<EpgSource> = (listOf(source) + fallbackSources).distinctBy { it.url }

    private val parsedCaches: Map<String, EpgParsedCache> =
        sources.associate { it.url to EpgParsedCache(it.url) }

    private val xmlRepositories: Map<String, EpgXmlRepository> =
        sources.associate { it.url to EpgXmlRepository(it.url) }

    /**
     * 清除全部来源的缓存
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        xmlRepositories.values.forEach { runCatching { it.clearCache() } }
        parsedCaches.values.forEach { runCatching { it.clearCache() } }
    }

    /**
     * 解析节目单xml
     */
    private suspend fun parseFromXml(
        xmlString: String,
        filteredChannels: List<String> = emptyList(),
    ) = withContext(Dispatchers.Default) {
        fun parseTime(time: String): Long {
            if (time.length < 14) return 0

            return SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault())
                .parse(time)?.time ?: 0
        }

        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xmlString))

        // 预先把待匹配频道归一化，避免遍历整份节目单时反复做正则运算
        val filteredNameSet = filteredChannels
            .map { ChannelName.normalize(it) }
            .filter { it.isNotBlank() }
            .toHashSet()

        val filteredCoreNameSet = filteredChannels
            .mapNotNull { ChannelName.coreKey(it) }
            .filter { it.startsWith("cctv") }
            .toHashSet()

        val epgMap = mutableMapOf<String, Epg>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "channel") {
                        val channelId = parser.getAttributeValue(null, "id")
                        parser.nextTag()
                        val channelName = parser.nextText()

                        if (isChannelWanted(channelName, filteredNameSet, filteredCoreNameSet)) {
                            epgMap[channelId] = Epg(channelName, EpgProgrammeList())
                        }
                    } else if (parser.name == "programme") {
                        val channelId = parser.getAttributeValue(null, "channel")
                        val startTime = parser.getAttributeValue(null, "start")
                        val stopTime = parser.getAttributeValue(null, "stop")
                        parser.nextTag()
                        val title = parser.nextText()

                        epgMap[channelId]?.let { epg ->
                            epgMap[channelId] = epg.copy(
                                programmeList = EpgProgrammeList(
                                    epg.programmeList + listOf(
                                        EpgProgramme(
                                            startAt = parseTime(startTime),
                                            endAt = parseTime(stopTime),
                                            title = title,
                                        )
                                    )
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        log.i("解析节目单完成，共${epgMap.size}个频道，${epgMap.values.sumOf { it.programmeList.size }}个节目")
        return@withContext EpgList(epgMap.values.toList())
    }

    /**
     * 判断节目单里的频道是否为当前直播源需要的频道
     *
     * 与 [top.yogiczy.mytv.core.data.entities.epg.EpgList.match] 保持同一套判定规则：
     * 先按归一化名称精确比较，央视系列再退化为核心标识比较。
     * 这里使用预先算好的集合，避免在遍历整份节目单时重复做正则运算。
     */
    private fun isChannelWanted(
        rawName: String,
        filteredNameSet: Set<String>,
        filteredCoreNameSet: Set<String>,
    ): Boolean {
        if (filteredNameSet.isEmpty()) return false

        val normalized = ChannelName.normalize(rawName)
        if (normalized.isBlank()) return false
        if (normalized in filteredNameSet) return true

        val core = ChannelName.coreKey(rawName) ?: return false
        return core.startsWith("cctv") && core in filteredCoreNameSet
    }

    /**
     * 获取节目单列表
     */
    suspend fun getEpgList(
        filteredChannels: List<String> = emptyList(),
        refreshTimeThreshold: Int,
    ): EpgList = withContext(Dispatchers.Default) {
        try {
            // 刷新时间点之前优先沿用已有缓存，避免凌晨去拉源站还没生成的数据。
            // 注意这里只是「不主动刷新」，绝不再返回空节目单。
            val beforeThreshold =
                Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < refreshTimeThreshold

            var merged: EpgList? = null

            for (epgSource in sources) {
                // 单个来源不可用（超时、返回旧文件、格式异常）时跳过，不要让整份节目单一起消失
                val loaded = runCatching { load(epgSource, filteredChannels, beforeThreshold) }
                    .onFailure { log.e("节目单来源不可用：${epgSource.name}", it) }
                    .getOrNull() ?: continue

                val current = merged
                if (current == null) {
                    // 第一个可用的来源作为主源
                    merged = loaded
                    continue
                }

                // 后续来源只用来补「当前时刻查不到节目」的频道
                val missing = current.channelsWithoutLiveProgramme(filteredChannels)
                if (missing.isEmpty()) break

                merged = current.fillMissingFrom(loaded, missing)
            }

            val epgList = merged ?: throw Exception("所有节目单来源都不可用")

            log.i(
                buildString {
                    append("节目单就绪：来源=").append(sources.size).append("个")
                    append("，频道").append(epgList.size).append("个")
                    append("，节目").append(epgList.sumOf { it.programmeList.size }).append("个")
                    if (filteredChannels.isNotEmpty()) {
                        append("，当前时刻覆盖 ")
                        append(epgList.liveChannelCount(filteredChannels))
                        append("/").append(filteredChannels.size)
                    }
                }
            )

            return@withContext epgList
        } catch (ex: Exception) {
            log.e("获取节目单失败", ex)
            throw Exception(ex)
        }
    }

    /**
     * 读取单个来源的节目单（优先用缓存）
     */
    private suspend fun load(
        epgSource: EpgSource,
        filteredChannels: List<String>,
        beforeThreshold: Boolean,
    ): EpgList {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val json = parsedCaches.getValue(epgSource.url).load(
            isExpired = { lastModified, cacheData ->
                when {
                    // 没有缓存 → 必须拉取
                    cacheData.isNullOrBlank() -> true
                    // 缓存里当前时刻一个节目都对不上 → 源多半已经更新，重新拉取
                    filteredChannels.isNotEmpty() &&
                            Json.decodeFromString<EpgList>(cacheData)
                                .liveChannelCount(filteredChannels) == 0 -> true
                    // 刷新时间点之前不主动刷新，沿用跨天缓存
                    beforeThreshold -> false
                    // 跨天 → 刷新
                    else -> dateFormat.format(System.currentTimeMillis()) !=
                            dateFormat.format(lastModified)
                }
            },
            refreshOp = {
                Json.encodeToString(
                    parseFromXml(
                        xmlRepositories.getValue(epgSource.url).getEpgXml(),
                        filteredChannels,
                    )
                )
            },
        )

        return Json.decodeFromString(json)
    }

    /**
     * 取「当前时刻正在播」的频道标识集合（归一化名 + 央视核心标识）
     *
     * 预先算一次集合，避免逐个频道去遍历整份节目单。
     */
    private fun EpgList.liveChannelKeys(now: Long = System.currentTimeMillis()): Set<String> {
        val keys = HashSet<String>()

        for (epg in this) {
            if (epg.programmeList.none { it.startAt <= now && now <= it.endAt }) continue

            ChannelName.normalize(epg.channel).takeIf { it.isNotBlank() }?.let { keys.add(it) }
            ChannelName.coreKey(epg.channel)?.takeIf { it.startsWith("cctv") }?.let { keys.add(it) }
        }

        return keys
    }

    /** 频道名是否落在上面算出来的集合里 */
    private fun Set<String>.covers(channelName: String): Boolean {
        val normalized = ChannelName.normalize(channelName)
        if (normalized.isNotBlank() && normalized in this) return true

        val core = ChannelName.coreKey(channelName) ?: return false
        return core.startsWith("cctv") && core in this
    }

    /** 当前时刻已经能查到节目的频道数 */
    private fun EpgList.liveChannelCount(names: List<String>): Int {
        val keys = liveChannelKeys()
        return names.count { keys.covers(it) }
    }

    /** 当前时刻还查不到节目的频道 */
    private fun EpgList.channelsWithoutLiveProgramme(names: List<String>): List<String> {
        val keys = liveChannelKeys()
        return names.filterNot { keys.covers(it) }
    }

    /**
     * 用补齐源的数据替换掉主源中「当前时刻没有节目」的频道
     */
    private fun EpgList.fillMissingFrom(extra: EpgList, missingNames: List<String>): EpgList {
        if (isEmpty()) return extra
        if (extra.isEmpty() || missingNames.isEmpty()) return this

        val replacements = LinkedHashMap<String, Epg>()

        missingNames.forEach { name ->
            val matched = extra.firstOrNull { epg ->
                epg.programmeList.isNotEmpty() && ChannelName.matches(epg.channel, name)
            } ?: return@forEach

            replacements[name] = matched
        }

        if (replacements.isEmpty()) return this

        val kept = filterNot { epg -> missingNames.any { ChannelName.matches(epg.channel, it) } }
        val added = replacements.values.distinct()

        return EpgList(kept + added)
    }
}

/**
 * 单个来源的解析结果缓存
 *
 * 与 xml 缓存分开放，避免把「下载到的原始 xml」和「过滤后的解析结果」混在一起。
 */
private class EpgParsedCache(url: String) :
    FileCacheRepository("epg-${url.hashCode().toUInt().toString(16)}.json") {

    suspend fun load(
        isExpired: (lastModified: Long, cacheData: String?) -> Boolean,
        refreshOp: suspend () -> String,
    ): String = getOrRefresh(isExpired, refreshOp)
}

/**
 * 节目单xml获取
 */
private class EpgXmlRepository(
    private val url: String
) : FileCacheRepository("epg-${url.hashCode().toUInt().toString(16)}.xml") {
    private val log = Logger.create(javaClass.simpleName)

    /**
     * 获取远程xml
     */
    private suspend fun fetchXml(): String {
        log.i("获取节目单xml: $url")

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        try {
            val response = client.newCall(request).await()

            if (!response.isSuccessful) throw Exception("${response.code}: ${response.message}")

            val fetcher = EpgFetcher.instances.first { it.isSupport(url) }
            return withContext(Dispatchers.IO) {
                fetcher.fetch(response)
            }
        } catch (ex: Exception) {
            log.e("获取节目单xml失败", ex)
            throw Exception("获取节目单xml失败，请检查网络连接", ex)
        }
    }

    /**
     * 获取xml
     */
    suspend fun getEpgXml(): String {
        return getOrRefresh(0) {
            fetchXml()
        }
    }
}
