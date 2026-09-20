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
 */
class EpgRepository(
    source: EpgSource,
) : FileCacheRepository("epg-${source.url.hashCode().toUInt().toString(16)}.json") {
    private val log = Logger.create(javaClass.simpleName)
    private val epgXmlRepository = EpgXmlRepository(source.url)

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
            if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < refreshTimeThreshold) {
                log.i("未到时间点，不刷新节目单")
                return@withContext EpgList()
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val xmlJson = getOrRefresh({ lastModified, _ ->
                dateFormat.format(System.currentTimeMillis()) != dateFormat.format(lastModified)
            }) {
                val xmlString = epgXmlRepository.getEpgXml()
                Json.encodeToString(
                    parseFromXml(
                        xmlString,
                        // 归一化在 parseFromXml 内部完成，这里传原始名称即可
                        filteredChannels,
                    )
                )
            }

            return@withContext Json.decodeFromString(xmlJson)
        } catch (ex: Exception) {
            log.e("获取节目单失败", ex)
            throw Exception(ex)
        }
    }
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
