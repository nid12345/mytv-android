package top.yogiczy.mytv.core.data.repositories.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yogiczy.mytv.core.data.entities.git.GitRelease
import top.yogiczy.mytv.core.data.network.await
import top.yogiczy.mytv.core.data.repositories.git.parser.GitReleaseParser
import top.yogiczy.mytv.core.data.utils.Loggable
import java.util.concurrent.TimeUnit

/**
 * git数据获取
 */
class GitRepository : Loggable() {

    /**
     * 获取最新发行版
     */
    suspend fun latestRelease(url: String): GitRelease = latestRelease(listOf(url))

    /**
     * 获取最新发行版（多个地址按顺序回退）
     *
     * 国内直连 GitHub 接口经常失败，调用方会给出「直连 + 镜像」一串地址，
     * 这里逐个尝试，第一个成功的就返回；全部失败才抛异常。
     */
    suspend fun latestRelease(urls: List<String>): GitRelease {
        var lastError: Exception? = null

        for (url in urls) {
            try {
                return latestReleaseFrom(url)
            } catch (ex: Exception) {
                lastError = ex
                log.w("获取最新发行版失败，改用下一个地址: $url")
            }
        }

        log.e("获取最新发行版失败", lastError)
        throw Exception("获取最新发行版失败，请检查网络连接", lastError)
    }

    private suspend fun latestReleaseFrom(url: String): GitRelease {
        log.d("获取最新发行版: $url")

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(url).build()

        try {
            val response = client.newCall(request).await()

            if (!response.isSuccessful) throw Exception("${response.code}: ${response.message}")

            val parser = GitReleaseParser.instances.first { it.isSupport(url) }
            return withContext(Dispatchers.IO) {
                parser.parse(response.body!!.string())
            }
        } catch (ex: Exception) {
            log.e("获取最新发行版失败", ex)
            throw Exception("获取最新发行版失败，请检查网络连接", ex)
        }
    }
}
