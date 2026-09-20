package top.yogiczy.mytv.core.data.repositories.dlna

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import top.yogiczy.mytv.core.data.entities.dlna.DlnaDevice
import top.yogiczy.mytv.core.data.utils.Loggable
import java.io.StringReader
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * DLNA 设备发现
 *
 * 通过 SSDP（简单服务发现协议）向局域网多播地址发送 M-SEARCH 搜索报文，
 * 收集响应中的设备描述地址，再解析设备描述 XML 得到 AVTransport 控制地址。
 *
 * 只保留声明了 AVTransport 服务（即具备播放能力）的设备，
 * 过滤掉只提供 ContentDirectory 的纯媒体服务器（如 NAS）。
 */
object DlnaDiscovery : Loggable() {
    private const val SSDP_HOST = "239.255.255.250"
    private const val SSDP_PORT = 1900

    /**
     * 重复发送搜索报文的间隔（毫秒）
     *
     * UDP 多播不可靠，单次发送容易漏包，需要重复发送以提升发现率
     */
    private const val SEARCH_INTERVAL_MS = 700L

    /**
     * 单次接收的超时时间（毫秒）
     */
    private const val RECEIVE_TIMEOUT_MS = 400

    /**
     * 搜索目标：只搜索媒体渲染器
     */
    private val SEARCH_TARGETS = listOf(
        "urn:schemas-upnp-org:device:MediaRenderer:1",
        "urn:schemas-upnp-org:device:MediaRenderer:2",
        "urn:schemas-upnp-org:device:MediaRenderer:3",
    )

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 搜索局域网内的投屏设备
     *
     * @param timeoutMs 搜索持续时间
     * @param onDeviceFound 每解析出一台设备即回调，用于界面增量展示
     */
    suspend fun discover(
        context: Context?,
        timeoutMs: Long = 6000,
        onDeviceFound: (DlnaDevice) -> Unit = {},
    ): List<DlnaDevice> = withContext(Dispatchers.IO) {
        val multicastLock = acquireMulticastLock(context)
        val socket = createSocket()

        if (socket == null) {
            runCatching { multicastLock?.release() }
            return@withContext emptyList()
        }

        val locations = LinkedHashMap<String, String>()

        try {
            val buffer = ByteArray(8192)
            val startedAt = System.currentTimeMillis()
            var lastSentAt = 0L

            while (System.currentTimeMillis() - startedAt < timeoutMs) {
                val now = System.currentTimeMillis()
                if (now - lastSentAt >= SEARCH_INTERVAL_MS) {
                    SEARCH_TARGETS.forEach { sendSearch(socket, it) }
                    lastSentAt = now
                }

                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    val response = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val location = response.headerValue("LOCATION") ?: continue
                    val key = response.headerValue("USN")
                        ?.substringBefore("::")
                        ?.ifBlank { null }
                        ?: location

                    if (locations.put(key, location) == null) {
                        log.d("收到设备响应: $location")
                    }
                } catch (_: SocketTimeoutException) {
                    // 本轮未收到报文，继续等待直到总超时
                } catch (ex: Exception) {
                    log.w("接收多播响应失败: ${ex.message}")
                }
            }
        } finally {
            runCatching { socket.close() }
            runCatching { multicastLock?.release() }
        }

        val devices = coroutineScope {
            locations.values.distinct()
                .map { location ->
                    async { fetchDevice(location)?.also(onDeviceFound) }
                }
                .awaitAll()
                .filterNotNull()
        }

        log.i("发现 ${devices.size} 台投屏设备")
        devices.sortedBy { it.displayName }
    }

    private fun createSocket(): MulticastSocket? {
        return try {
            MulticastSocket().apply {
                reuseAddress = true
                soTimeout = RECEIVE_TIMEOUT_MS
                timeToLive = 4
                pickNetworkInterface()?.let { runCatching { networkInterface = it } }
            }
        } catch (ex: Exception) {
            log.e("创建多播套接字失败: ${ex.message}", ex)
            null
        }
    }

    /**
     * 选择多播发送使用的网络接口
     *
     * 设备同时在蜂窝网络与 WiFi 时，需要显式选择局域网接口，否则多播报文发不出去
     */
    private fun pickNetworkInterface(): NetworkInterface? {
        val enumeration = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull()
            ?: return null

        val candidates = mutableListOf<NetworkInterface>()
        while (enumeration.hasMoreElements()) {
            val intf = enumeration.nextElement()
            val hasIpv4 = runCatching {
                intf.interfaceAddresses.any { it.address is Inet4Address }
            }.getOrDefault(false)

            if (intf.isUp && !intf.isLoopback && intf.supportsMulticast() && hasIpv4) {
                candidates += intf
            }
        }

        return candidates.firstOrNull { it.name.startsWith("wlan") }
            ?: candidates.firstOrNull { it.name.startsWith("eth") }
            ?: candidates.firstOrNull { it.name.startsWith("en") }
            ?: candidates.firstOrNull()
    }

    /**
     * 获取 WiFi 多播锁
     *
     * Android 默认会在屏幕关闭后过滤多播报文，未持锁时 SSDP 响应会大量丢失
     */
    private fun acquireMulticastLock(context: Context?): WifiManager.MulticastLock? {
        return try {
            val appContext = context?.applicationContext ?: return null
            val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

            wifiManager?.createMulticastLock("mytv-dlna")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (ex: Exception) {
            log.w("获取多播锁失败: ${ex.message}")
            null
        }
    }

    private fun sendSearch(socket: MulticastSocket, searchTarget: String) {
        // SSDP 属 HTTP 系协议，行分隔必须是 CRLF。
        // 不要用 appendLine()：它在 Android 上产出的是 LF，部分固件（三星/LG 等）会因此丢弃报文
        val message = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $SSDP_HOST:$SSDP_PORT\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 2\r\n")
            append("ST: $searchTarget\r\n")
            append("USER-AGENT: Android/1.0 UPnP/1.1 MyTV/1.0\r\n")
            append("\r\n")
        }

        val bytes = message.toByteArray(Charsets.UTF_8)
        try {
            socket.send(
                DatagramPacket(
                    bytes,
                    bytes.size,
                    InetAddress.getByName(SSDP_HOST),
                    SSDP_PORT,
                )
            )
        } catch (ex: Exception) {
            log.w("发送搜索报文失败: ${ex.message}")
        }
    }

    private fun fetchDevice(location: String): DlnaDevice? {
        return try {
            val request = Request.Builder()
                .url(location)
                .header("Connection", "close")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    log.d("设备描述请求失败 ${response.code}: $location")
                    return null
                }

                val xml = response.body?.string() ?: return null
                parseDeviceDescription(location, xml)
            }
        } catch (ex: Exception) {
            log.d("获取设备描述失败: ${ex.message}")
            null
        }
    }

    private fun parseDeviceDescription(location: String, xml: String): DlnaDevice? {
        var friendlyName = ""
        var modelName = ""
        var udn = ""

        var avTransportServiceType = ""
        var avTransportControlUrl = ""
        var renderingControlServiceType = ""
        var renderingControlControlUrl = ""

        var inService = false
        var serviceType = ""
        var controlUrl = ""

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "friendlyName" -> if (friendlyName.isBlank()) {
                            friendlyName = parser.nextText().trim()
                        }

                        "modelName" -> if (modelName.isBlank()) {
                            modelName = parser.nextText().trim()
                        }

                        "UDN" -> if (udn.isBlank()) {
                            udn = parser.nextText().trim()
                        }

                        "service" -> {
                            inService = true
                            serviceType = ""
                            controlUrl = ""
                        }

                        "serviceType" -> if (inService) {
                            serviceType = parser.nextText().trim()
                        }

                        "controlURL" -> if (inService) {
                            controlUrl = parser.nextText().trim()
                        }
                    }

                    XmlPullParser.END_TAG -> if (parser.name == "service" && inService) {
                        inService = false

                        if (serviceType.contains("AVTransport") && avTransportControlUrl.isBlank()) {
                            avTransportServiceType = serviceType
                            avTransportControlUrl = controlUrl
                        }

                        if (serviceType.contains("RenderingControl") && renderingControlControlUrl.isBlank()) {
                            renderingControlServiceType = serviceType
                            renderingControlControlUrl = controlUrl
                        }
                    }
                }
                event = parser.next()
            }
        } catch (ex: Exception) {
            log.d("解析设备描述失败: ${ex.message}")
            return null
        }

        // 没有 AVTransport 服务说明设备不支持被推送播放，直接忽略
        if (avTransportControlUrl.isBlank()) return null

        return DlnaDevice(
            friendlyName = friendlyName,
            modelName = modelName,
            udn = udn,
            location = location,
            host = runCatching { URI(location).host.orEmpty() }.getOrDefault(""),
            avTransportServiceType = avTransportServiceType
                .ifBlank { "urn:schemas-upnp-org:service:AVTransport:1" },
            avTransportControlUrl = resolveUrl(location, avTransportControlUrl),
            renderingControlServiceType = renderingControlServiceType,
            renderingControlControlUrl = if (renderingControlControlUrl.isBlank()) {
                ""
            } else {
                resolveUrl(location, renderingControlControlUrl)
            },
        )
    }

    /**
     * 解析控制地址
     *
     * 设备描述中的 controlURL 可能是相对路径，需要与描述地址拼接
     */
    private fun resolveUrl(location: String, controlUrl: String): String {
        return runCatching {
            val resolved = URI(location).resolve(controlUrl)
            resolved.toString()
        }.getOrDefault(controlUrl)
    }

    private fun String.headerValue(name: String): String? {
        return lineSequence()
            .firstOrNull { it.startsWith("$name:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.ifBlank { null }
    }
}
