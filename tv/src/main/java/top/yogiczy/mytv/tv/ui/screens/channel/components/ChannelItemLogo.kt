package top.yogiczy.mytv.tv.ui.screens.channel.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage

/**
 * 频道台标
 *
 * 台标按下面的顺序依次尝试，前一个加载失败时自动回退到下一个，
 * 避免因某个域名不可达而导致整屏频道都没有台标：
 *
 * 1. 安装包内置台标（assets/channel_logo）——常用频道，完全离线、瞬时显示；
 * 2. 自建台标仓库，经公共 CDN 加速——仓库地址见 Constants.CHANNEL_LOGO_SOURCE；
 * 3. 自建台标仓库，直连地址；
 * 4. 上游台标原站；
 * 5. 上游台标的国内镜像。
 */
@Composable
fun ChannelItemLogo(
    modifier: Modifier = Modifier,
    logoProvider: () -> String?,
) {
    val context = LocalContext.current
    val requested = logoProvider()
    val candidates = remember(requested) { logoCandidates(context, requested) }

    ChannelItemLogoAt(
        modifier = modifier,
        candidates = candidates,
        index = 0,
    )
}

@Composable
private fun ChannelItemLogoAt(
    modifier: Modifier,
    candidates: List<String>,
    index: Int,
) {
    val url = candidates.getOrNull(index) ?: return

    SubcomposeAsyncImage(
        modifier = modifier,
        model = url,
        contentDescription = null,
        error = {
            if (index < candidates.lastIndex) {
                ChannelItemLogoAt(
                    modifier = modifier,
                    candidates = candidates,
                    index = index + 1,
                )
            }
        },
    )
}

/** 内置台标所在目录（assets 下） */
private const val LOGO_ASSET_DIR = "channel_logo"

/** 自建台标仓库：公共 CDN 加速地址 */
private const val LOGO_SELF_CDN_PREFIX =
    "https://gcore.jsdelivr.net/gh/nid12345/mytv-logos@main/tv/"

/** 自建台标仓库：直连地址 */
private const val LOGO_SELF_RAW_PREFIX =
    "https://raw.githubusercontent.com/nid12345/mytv-logos/main/tv/"

/** 上游台标原站 */
private const val LOGO_UPSTREAM_PREFIX = "https://live.fanmingming.com/tv/"

/** 上游台标的国内镜像 */
private const val LOGO_GITEE_MIRROR_PREFIX = "https://gitee.com/sujivin/live/raw/main/tv/"

/** 台标仓库族前缀：只有来自台标仓库的地址才做多地址尝试 */
private val LOGO_REPO_PREFIXES = listOf(
    LOGO_SELF_CDN_PREFIX,
    LOGO_SELF_RAW_PREFIX,
    LOGO_UPSTREAM_PREFIX,
    LOGO_GITEE_MIRROR_PREFIX,
)

/** 内置台标索引：文件名（去扩展名、转小写）→ assets 中的真实文件名 */
private object BuiltinLogoIndex {
    @Volatile
    private var cache: Map<String, String>? = null

    fun of(context: Context): Map<String, String> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val index = buildMap {
                context.assets.list(LOGO_ASSET_DIR)?.forEach { fileName ->
                    put(fileName.substringBeforeLast('.').lowercase(), fileName)
                }
            }
            cache = index
            return index
        }
    }
}

/**
 * 生成同一个台标的候选地址
 *
 * 内置台标优先返回，命中时无需再访问网络；地址不来自台标仓库时保持原样，
 * 避免对用户自定义的台标地址做无谓替换。
 */
private fun logoCandidates(context: Context, primary: String?): List<String> {
    if (primary.isNullOrBlank()) return emptyList()

    val baseName = primary.substringAfterLast('/').substringBeforeLast('.')
    val candidates = mutableListOf<String>()

    BuiltinLogoIndex.of(context)[baseName.lowercase()]?.let { builtin ->
        candidates += "file:///android_asset/$LOGO_ASSET_DIR/$builtin"
    }

    if (baseName.isNotBlank() && LOGO_REPO_PREFIXES.any { primary.startsWith(it) }) {
        candidates += LOGO_SELF_CDN_PREFIX + baseName + ".png"
        candidates += LOGO_SELF_RAW_PREFIX + baseName + ".png"
        candidates += LOGO_UPSTREAM_PREFIX + baseName + ".png"
        candidates += LOGO_GITEE_MIRROR_PREFIX + baseName + ".png"
    } else {
        candidates += primary
    }

    return candidates
}
