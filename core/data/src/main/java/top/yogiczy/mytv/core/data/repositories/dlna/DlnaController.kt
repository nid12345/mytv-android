package top.yogiczy.mytv.core.data.repositories.dlna

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import top.yogiczy.mytv.core.data.entities.dlna.DlnaDevice
import top.yogiczy.mytv.core.data.utils.Loggable
import java.util.concurrent.TimeUnit

/**
 * DLNA 投送控制
 *
 * 通过 UPnP AVTransport 服务控制远端设备播放指定地址：
 * 先 SetAVTransportURI 设置播放地址与元数据，再 Play 开始播放。
 */
object DlnaController : Loggable() {
    private const val INSTANCE_ID = "0"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 把直播源投送到设备播放
     *
     * 先 SetAVTransportURI 设置播放地址与元数据，再 Play 开始播放。
     */
    suspend fun cast(
        device: DlnaDevice,
        url: String,
        title: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (url.isBlank()) throw DlnaException("当前频道没有可用的播放地址")

            val metadata = buildDidlLite(url, title)

            val setUriSucceeded = runCatching {
                invoke(
                    device = device,
                    action = "SetAVTransportURI",
                    arguments = listOf(
                        "InstanceID" to INSTANCE_ID,
                        "CurrentURI" to url,
                        "CurrentURIMetaData" to metadata,
                    ),
                )
            }.fold(
                onSuccess = { true },
                onFailure = { ex ->
                    // 部分设备不理解元数据，退化为不带元数据再试一次
                    log.w("携带元数据投送失败，改为不带元数据重试: ${ex.message}")

                    runCatching {
                        invoke(
                            device = device,
                            action = "SetAVTransportURI",
                            arguments = listOf(
                                "InstanceID" to INSTANCE_ID,
                                "CurrentURI" to url,
                                "CurrentURIMetaData" to "",
                            ),
                        )
                    }.isSuccess
                },
            )

            if (!setUriSucceeded) throw DlnaException("设备拒绝播放该地址")

            // 部分设备设置播放地址后需要短暂处理才能接受播放指令
            delay(300)

            invoke(
                device = device,
                action = "Play",
                arguments = listOf(
                    "InstanceID" to INSTANCE_ID,
                    "Speed" to "1",
                ),
            )

            log.i("已投送到 ${device.displayName}: $title")
            Result.success(Unit)
        } catch (ex: Exception) {
            log.e("投送失败: ${ex.message}")
            Result.failure(ex)
        }
    }

    /**
     * 停止设备播放
     */
    suspend fun stop(device: DlnaDevice): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            invoke(
                device = device,
                action = "Stop",
                arguments = listOf("InstanceID" to INSTANCE_ID),
            )
            true
        }.getOrElse {
            log.w("停止投送失败: ${it.message}")
            false
        }
    }

    /**
     * 查询设备播放状态，如 PLAYING / STOPPED / NO_MEDIA_PRESENT
     */
    suspend fun getTransportState(device: DlnaDevice): String? = withContext(Dispatchers.IO) {
        runCatching {
            val response = invoke(
                device = device,
                action = "GetTransportInfo",
                arguments = listOf("InstanceID" to INSTANCE_ID),
            )

            Regex("<CurrentTransportState>([^<]*)</CurrentTransportState>")
                .find(response)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
        }.getOrNull()
    }

    private suspend fun invoke(
        device: DlnaDevice,
        action: String,
        arguments: List<Pair<String, String>>,
    ): String = withContext(Dispatchers.IO) {
        val serviceType = device.avTransportServiceType
        val controlUrl = device.avTransportControlUrl

        if (controlUrl.isBlank()) throw DlnaException("设备不支持 AVTransport 控制")

        val args = arguments.joinToString("\n") { (key, value) ->
            "<$key>${value.xmlEscape()}</$key>"
        }

        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
            <s:Body>
            <u:$action xmlns:u="$serviceType">
            $args
            </u:$action>
            </s:Body>
            </s:Envelope>
        """.trimIndent()

        val request = Request.Builder()
            .url(controlUrl)
            .header("Content-Type", "text/xml; charset=\"utf-8\"")
            .header("SOAPAction", "\"$serviceType#$action\"")
            .header("Connection", "close")
            .post(body.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val description = Regex("<errorDescription>([^<]*)</errorDescription>")
                    .find(text)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()
                    ?.ifBlank { null }

                val code = Regex("<errorCode>([^<]*)</errorCode>")
                    .find(text)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()

                throw DlnaException(
                    description
                        ?: code?.let { "设备返回错误码 $it" }
                        ?: "设备响应异常（HTTP ${response.code}）"
                )
            }

            text
        }
    }

    /**
     * 构造播放元数据
     *
     * 部分设备缺少元数据时会拒绝播放，需要提供标准的 DIDL-Lite 描述
     */
    private fun buildDidlLite(url: String, title: String): String {
        val safeTitle = title.ifBlank { "直播" }

        return """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
            <item id="0" parentID="-1" restricted="1">
            <dc:title>${safeTitle.xmlEscape()}</dc:title>
            <upnp:class>object.item.videoItem</upnp:class>
            <res protocolInfo="${guessProtocolInfo(url)}">${url.xmlEscape()}</res>
            </item>
            </DIDL-Lite>
        """.trimIndent()
    }

    private fun guessProtocolInfo(url: String): String {
        val format = when {
            url.contains(".m3u8", ignoreCase = true) -> "application/vnd.apple.mpegurl"
            url.contains(".mp4", ignoreCase = true) -> "video/mp4"
            url.contains(".mkv", ignoreCase = true) -> "video/x-matroska"
            url.contains(".flv", ignoreCase = true) -> "video/x-flv"
            url.contains(".ts", ignoreCase = true) -> "video/mp2t"
            else -> "video/*"
        }

        return "http-get:*:$format:*"
    }

    private fun String.xmlEscape(): String {
        return replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}

/**
 * DLNA 投送异常
 */
class DlnaException(message: String) : Exception(message)
