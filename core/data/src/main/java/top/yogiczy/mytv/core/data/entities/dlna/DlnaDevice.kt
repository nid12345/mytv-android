package top.yogiczy.mytv.core.data.entities.dlna

import androidx.compose.runtime.Immutable

/**
 * DLNA 媒体渲染设备（智能电视、电视盒子、投影仪等）
 *
 * 通过 SSDP 发现后解析设备描述 XML 得到控制地址，
 * 再通过 AVTransport 服务让设备播放指定的直播源地址。
 */
@Immutable
data class DlnaDevice(
    /**
     * 设备名称，如「客厅电视」
     */
    val friendlyName: String = "",

    /**
     * 设备型号
     */
    val modelName: String = "",

    /**
     * 设备唯一标识
     */
    val udn: String = "",

    /**
     * 设备描述地址
     */
    val location: String = "",

    /**
     * 设备 IP
     */
    val host: String = "",

    /**
     * AVTransport 服务类型（含版本号，用于构造 SOAPACTION）
     */
    val avTransportServiceType: String = "",

    /**
     * AVTransport 控制地址
     */
    val avTransportControlUrl: String = "",

    /**
     * RenderingControl 服务类型
     */
    val renderingControlServiceType: String = "",

    /**
     * RenderingControl 控制地址
     */
    val renderingControlControlUrl: String = "",
) {
    /**
     * 设备唯一标识
     */
    val id: String get() = udn.ifBlank { location }

    /**
     * 展示用设备名
     */
    val displayName: String
        get() = friendlyName.ifBlank {
            host.ifBlank { "未知设备" }
        }

    /**
     * 展示用设备信息
     */
    val displayInfo: String
        get() = listOf(host, modelName)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
}
