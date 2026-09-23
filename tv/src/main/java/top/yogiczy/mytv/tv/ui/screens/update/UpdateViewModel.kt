package top.yogiczy.mytv.tv.ui.screens.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import top.yogiczy.mytv.core.data.entities.git.GitRelease
import top.yogiczy.mytv.core.data.repositories.git.GitRepository
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.core.util.utils.Downloader
import top.yogiczy.mytv.core.util.utils.compareVersion
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import top.yogiczy.mytv.tv.ui.utils.Configs
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream

class UpdateViewModel : ViewModel() {
    private val log = Logger.create(javaClass.simpleName)

    private var _isChecking by mutableStateOf(false)
    val isChecking get() = _isChecking

    private var _isUpdating by mutableStateOf(false)
    val isUpdating get() = _isUpdating

    private var _downloadProgress by mutableIntStateOf(0)
    val downloadProgress get() = _downloadProgress

    private var _isUpdateAvailable by mutableStateOf(false)
    val isUpdateAvailable get() = _isUpdateAvailable

    private var _updateDownloaded by mutableStateOf(false)
    val updateDownloaded get() = _updateDownloaded

    private var _hasChecked by mutableStateOf(false)
    val hasChecked get() = _hasChecked

    private var _latestRelease by mutableStateOf(GitRelease())
    val latestRelease get() = _latestRelease

    /** 显示更新面板 */
    var visible by mutableStateOf(false)

    /**
     * 手动检查更新的请求计数
     *
     * 设置里点「检查更新」时 +1，界面（UpdateScreen）据此强制重新检查一次。
     * 计数放在 ViewModel 里，设置页与更新面板用的是同一个实例。
     */
    var manualCheckRequest by mutableIntStateOf(0)

    /**
     * 检查更新
     *
     * 检查地址是一串「直连 + 镜像」，由 [GitRepository] 逐个尝试。
     *
     * @param force 手动检查时忽略「已经发现新版本」的短路，失败时也给出提示
     */
    suspend fun checkUpdate(currentVersion: String, channel: String, force: Boolean = false) {
        if (_isChecking) return
        if (!force && _isUpdateAvailable) return

        val urls = Constants.RELEASE_CHECK_URLS[channel] ?: return

        try {
            _isChecking = true
            _latestRelease = GitRepository().latestRelease(urls)
            _hasChecked = true

            log.i("线上版本: ${_latestRelease.version}（当前: $currentVersion）")

            _isUpdateAvailable = runCatching {
                _latestRelease.version.compareVersion(currentVersion) > 0 &&
                        _latestRelease.downloadUrl.isNotBlank()
            }.getOrElse {
                log.e("版本比较失败: ${_latestRelease.version} vs $currentVersion", it)
                false
            }
        } catch (ex: Exception) {
            log.e("检查更新失败", ex)
            if (force) Snackbar.show("检查更新失败，请检查网络连接", type = SnackbarType.ERROR)
        } finally {
            _isChecking = false
        }
    }

    /**
     * 下载更新包
     *
     * 按设置里的「下载线路」给出候选地址（直连 / 加速镜像），逐条尝试；
     * 每条下载完先校验是不是真的 APK——镜像挂掉时常会返回一个 HTML 错误页，
     * 不校验的话会把错误页当安装包丢给系统安装器。全部失败才报错。
     */
    suspend fun downloadAndUpdate(latestFile: File) {
        if (!_isUpdateAvailable) return
        if (_isUpdating) return

        _isUpdating = true
        _updateDownloaded = false
        _downloadProgress = 0

        Snackbar.show("开始下载更新", leadingLoading = true, duration = 10_000, id = SNACKBAR_ID)

        try {
            val urls = downloadCandidateUrls(_latestRelease.downloadUrl)
            var lastError: Exception? = null

            for ((index, url) in urls.withIndex()) {
                try {
                    log.i("下载更新包（${index + 1}/${urls.size}）: $url")
                    Downloader.downloadTo(url, latestFile.path) {
                        _downloadProgress = it
                        Snackbar.show(
                            "正在下载更新: $it%",
                            leadingLoading = true,
                            duration = 10_000,
                            id = SNACKBAR_ID,
                        )
                    }

                    if (isValidApk(latestFile)) {
                        _updateDownloaded = true
                        Snackbar.show("下载完成，开始安装")
                        return
                    }

                    throw Exception("下载到的文件不是安装包")
                } catch (ex: Exception) {
                    lastError = ex
                    log.w("下载失败，改用下一个地址: $url")
                    latestFile.delete()
                }
            }

            log.e("下载更新失败", lastError)
            Snackbar.show(
                "下载更新失败：${lastError?.message ?: "请检查网络连接"}",
                type = SnackbarType.ERROR,
            )
        } finally {
            _isUpdating = false
        }
    }

    /** 按「下载线路」设置给出候选地址（依次尝试） */
    private fun downloadCandidateUrls(downloadUrl: String): List<String> {
        val mirrors = Constants.RELEASE_DOWNLOAD_MIRRORS.map { it + downloadUrl }

        return when (Configs.updateDownloadRoute) {
            Configs.UpdateDownloadRoute.DIRECT -> listOf(downloadUrl)
            Configs.UpdateDownloadRoute.MIRROR -> mirrors + downloadUrl
            Configs.UpdateDownloadRoute.AUTO -> listOf(downloadUrl) + mirrors
        }
    }

    /** 校验确实是 APK：ZIP 头 `PK` 且体积合理 */
    private fun isValidApk(file: File): Boolean {
        if (!file.exists() || file.length() < MIN_APK_SIZE) return false

        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                input.readByte() == 'P'.code.toByte() && input.readByte() == 'K'.code.toByte()
            }
        }.getOrDefault(false)
    }

    companion object {
        private const val SNACKBAR_ID = "downloadProcess"

        /** 安装包体积下限，低于这个值肯定不是正常 APK */
        private const val MIN_APK_SIZE = 500 * 1024L
    }
}
