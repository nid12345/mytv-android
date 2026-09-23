package top.yogiczy.mytv.core.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yogiczy.mytv.core.data.repositories.git.parser.CustomReleaseParser

/**
 * 定制版更新接口的解析（升级通道的关键一环）
 *
 * 版本号解析错一步，整个「检测到新版本」就静默失效：
 * 定制版 tag 是 `custom-v1.7`，旧解析器 `substring(1)` 会得到 `ustom-v1.7`，
 * 后面版本比较按 `.` 切分转数字时直接抛异常。
 */
class CustomReleaseParserTest {
    private val parser = CustomReleaseParser()

    /** 真实接口响应形状（/releases/latest 返回对象，tag 带 custom- 前缀） */
    private val latestObjectJson = """
        {
          "tag_name": "custom-v1.6",
          "name": "定制版 1.6：线路测速两阶段实测",
          "body": "修复「默认直播源打开就卡」",
          "assets": [
            {
              "name": "MyTV-1.6.apk",
              "size": 5386757,
              "browser_download_url": "https://github.com/nid12345/mytv-android/releases/download/custom-v1.6/MyTV-1.6.apk"
            }
          ]
        }
    """.trimIndent()

    /** /releases 返回数组，取最新的一条 */
    private val releaseListJson = """
        [
          {
            "tag_name": "custom-v1.7",
            "body": "1.7",
            "assets": [
              { "name": "MyTV-1.7.apk", "browser_download_url": "https://example.com/MyTV-1.7.apk" },
              { "name": "checksums.txt", "browser_download_url": "https://example.com/checksums.txt" }
            ]
          },
          { "tag_name": "custom-v1.6", "assets": [] }
        ]
    """.trimIndent()

    @Test
    fun `识别定制版接口地址`() {
        assertTrue(parser.isSupport("https://api.github.com/repos/nid12345/mytv-android/releases/latest"))
        // 走加速镜像时地址里仍带着原始 api 地址，也要认得
        assertTrue(
            parser.isSupport(
                "https://gh-proxy.com/https://api.github.com/repos/nid12345/mytv-android/releases/latest"
            )
        )
    }

    @Test
    fun `解析 latest 对象响应`() = runBlocking {
        val release = parser.parse(latestObjectJson)

        assertEquals("1.6", release.version)
        assertEquals(
            "https://github.com/nid12345/mytv-android/releases/download/custom-v1.6/MyTV-1.6.apk",
            release.downloadUrl,
        )
        assertTrue(release.description.contains("默认直播源"))
    }

    @Test
    fun `解析 releases 数组响应并挑出 apk 附件`() = runBlocking {
        val release = parser.parse(releaseListJson)

        assertEquals("1.7", release.version)
        // 附件里第一个不是 apk 时，要跳过它
        assertEquals("https://example.com/MyTV-1.7.apk", release.downloadUrl)
    }

    @Test
    fun `版本号归一化`() {
        assertEquals("1.7", CustomReleaseParser.normalizeVersion("custom-v1.7"))
        assertEquals("1.7.1", CustomReleaseParser.normalizeVersion("v1.7.1"))
        assertEquals("2.0", CustomReleaseParser.normalizeVersion("2.0"))
        assertEquals("1.10", CustomReleaseParser.normalizeVersion("custom-v1.10"))
    }
}
