package top.yogiczy.mytv.tv.ui.screens.channel.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import coil.compose.SubcomposeAsyncImage

/**
 * 频道台标
 *
 * 台标仓库以海外域名为主，部分网络环境下不可达；一旦不可达，整屏频道都会没有台标。
 * 这里为同一个台标准备若干地址，按「国内镜像 → 原地址 → 公共 CDN」的顺序依次尝试，
 * 前一个加载失败时自动回退到下一个。
 */
@Composable
fun ChannelItemLogo(
    modifier: Modifier = Modifier,
    logoProvider: () -> String?,
) {
    val requested = logoProvider()
    val candidates = remember(requested) { logoCandidates(requested) }

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

/** 台标原地址前缀 */
private const val LOGO_PRIMARY_PREFIX = "https://live.fanmingming.com/tv/"

/** 国内镜像前缀，内容与原地址一致，大陆网络下可直连 */
private const val LOGO_MIRROR_PREFIX = "https://gitee.com/sujivin/live/raw/main/tv/"

/** 公共 CDN 前缀，作为最后的备选 */
private const val LOGO_CDN_PREFIX = "https://gcore.jsdelivr.net/gh/fanmingming/live@main/tv/"

/**
 * 生成同一个台标的候选地址
 *
 * 只在地址确实来自台标仓库时才追加备用地址，避免对用户自定义的台标地址做无谓的替换。
 */
private fun logoCandidates(primary: String?): List<String> {
    if (primary.isNullOrBlank()) return emptyList()

    val candidates = mutableListOf<String>()

    if (primary.startsWith(LOGO_PRIMARY_PREFIX)) {
        candidates += primary.replace(LOGO_PRIMARY_PREFIX, LOGO_MIRROR_PREFIX)
        candidates += primary
        candidates += primary.replace(LOGO_PRIMARY_PREFIX, LOGO_CDN_PREFIX)
    } else {
        candidates += primary
    }

    return candidates
}
