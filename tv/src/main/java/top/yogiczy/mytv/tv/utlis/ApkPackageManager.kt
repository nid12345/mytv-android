package top.yogiczy.mytv.tv.utlis

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.yogiczy.mytv.core.data.utils.SP
import java.io.File

/** 收到的安装包记录 */
@Serializable
data class ApkItem(
    val name: String,
    val path: String,
    val size: Long,

    /** 保存时间（毫秒时间戳） */
    val addedAt: Long,
)

/**
 * 推送收到的安装包管理
 *
 * 上传的 APK 会持久保存到 filesDir/apk 下（此前放在缓存目录且随进程退出删除，
 * 想装第二遍就得重传），并提供安装、删除。
 */
object ApkPackageManager : top.yogiczy.mytv.core.data.utils.Loggable() {
    private const val KEY = "APK_PACKAGE_LIST"
    private const val MAX_KEEP_COUNT = 20

    private val json = Json { ignoreUnknownKeys = true }

    fun dir(context: Context): File = File(context.filesDir, "apk").apply { mkdirs() }

    /** 已保存的安装包列表（自动过滤掉已被系统清理的文件） */
    fun list(context: Context): List<ApkItem> {
        val all = runCatching { json.decodeFromString<List<ApkItem>>(SP.getString(KEY, "[]")) }
            .getOrElse { emptyList() }
        val items = all.filter { File(it.path).exists() }

        // 顺手把记录里指向已消失文件的条目清理掉
        if (items.size != all.size) save(items)

        return items
    }

    private fun save(items: List<ApkItem>) {
        SP.putString(KEY, json.encodeToString(items))
    }

    /** 把上传完成的安装包挪进持久目录并登记 */
    fun saveUploadedApk(context: Context, src: File, displayName: String): ApkItem {
        val safeName = displayName.ifBlank { "uploaded.apk" }
        val dest = File(dir(context), "${System.currentTimeMillis()}-$safeName")
        src.copyTo(dest, overwrite = true)

        val item = ApkItem(
            name = dest.nameWithoutExtension.substringAfter('-'),
            path = dest.path,
            size = dest.length(),
            addedAt = System.currentTimeMillis(),
        )
        save((list(context) + item).takeLast(MAX_KEEP_COUNT))
        return item
    }

    /** 删除安装包文件与记录 */
    fun delete(item: ApkItem) {
        File(item.path).delete()
        save(
            runCatching { json.decodeFromString<List<ApkItem>>(SP.getString(KEY, "[]")) }
                .getOrElse { emptyList() } - item
        )
    }
}
