package top.yogiczy.mytv.core.data.repositories.git.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.yogiczy.mytv.core.data.entities.git.GitRelease

/**
 * 定制版发行版解析（GitHub Releases 接口）
 *
 * 与 [GithubGitReleaseParser] 的差别：
 * - 支持 `/releases/latest` 的对象响应，也支持 `/releases` 的数组响应（取第一个）；
 * - 版本号按数字段归一化，能吃下 `custom-v1.7` 这类定制版 tag
 *   （旧解析器直接 `tag_name.substring(1)`，会得到 `ustom-v1.7`，
 *   后续版本比较按 `.` 切分转数字时必抛异常）；
 * - 不拼接 GitHub 加速前缀，下载线路由下载流程按镜像列表逐条回退。
 */
class CustomReleaseParser : GitReleaseParser {
    override fun isSupport(url: String): Boolean = url.contains("api.github.com")

    override suspend fun parse(data: String): GitRelease {
        val element = Json.parseToJsonElement(data)

        // /releases/latest 返回单个对象；/releases 返回数组，取最新的一条
        val release = when (element) {
            is JsonArray -> element.firstOrNull()?.jsonObject
            else -> element.jsonObject
        } ?: throw Exception("发行版信息为空")

        val tag = release["tag_name"]?.jsonPrimitive?.content
            ?: throw Exception("发行版信息缺少 tag_name")

        val apkAsset = release["assets"]?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { asset ->
                asset["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true
            }

        return GitRelease(
            version = normalizeVersion(tag),
            downloadUrl = apkAsset?.get("browser_download_url")?.jsonPrimitive?.content.orEmpty(),
            description = release["body"]?.jsonPrimitive?.content.orEmpty(),
        )
    }

    companion object {
        /**
         * 版本号归一化：只保留其中的数字段
         *
         * `custom-v1.7` → `1.7`；`v1.7.1` → `1.7.1`；取不到数字时退化为去掉前缀的原串。
         */
        fun normalizeVersion(tag: String): String =
            Regex("""\d+(?:\.\d+)*""").find(tag)?.value
                ?: tag.trim().trimStart('v', 'V')
    }
}
