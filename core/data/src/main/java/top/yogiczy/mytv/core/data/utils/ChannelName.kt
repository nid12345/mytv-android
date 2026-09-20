package top.yogiczy.mytv.core.data.utils

import java.text.Normalizer

/**
 * 频道名称归一化
 *
 * 直播源与节目单往往来自不同提供方，同一个频道的写法经常不一致：
 * 直播源里多写作「CCTV-1」，节目单里多写作「CCTV1」；
 * 原实现按字符串精确比较（忽略大小写），这类写法差异会全部匹配失败，
 * 表现为节目单明明下载成功、界面上却一个节目都没有。
 *
 * 这里把两边都归一化成同一种形式再比较，不改变任何展示用的名称。
 */
object ChannelName {
    /** 中文数字 */
    private val cnDigits = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5,
        "六" to 6, "七" to 7, "八" to 8, "九" to 9, "十" to 10,
        "十一" to 11, "十二" to 12, "十三" to 13, "十四" to 14,
        "十五" to 15, "十六" to 16, "十七" to 17, "十八" to 18,
    )

    /** 中央电视台一台 / 中央1台 等 */
    private val cctvCnRegex = Regex("""中央(?:电视台|电视|台)?[\s\-_·]*([一二三四五六七八九十]{1,3}|\d{1,2})""")

    /** CCTV-1 / cctv 1 / CCTV5+ / CCTV5plus 等 */
    private val cctvRegex = Regex("""cctv[\s\-_·]*(\d{1,2})(\s*(?:\+|plus))?""")

    /** 画质后缀，与频道本身无关 */
    private val qualityRegex = Regex("""(超高清|高清|标清|超清|蓝光|fhd|uhd|hd)""")

    /** 各种各样的分隔与装饰字符 */
    private val noiseRegex = Regex("""[\s\-_·.,，。:：;；!！?？()（）\[\]【】{}"'“”‘’|/\\、＋]+""")

    /** CCTV 核心标识 */
    private val cctvCoreRegex = Regex("""cctv\d{1,2}(?:plus)?""")

    /**
     * 台标仓库中使用的完整央视标识：CCTV1 / CCTV5+ / CCTV4K / CCTV3D / CCTV8K
     *
     * 结尾的「台」来自「中央一台」这类写法归一化后的残留；必须整串匹配，
     * 否则「CCTV4欧洲」会被误当成「CCTV4」。
     */
    private val logoCctvRegex = Regex("""^cctv(\d{1,2}(?:plus)?|4k|3d|8k)台?$""")

    /**
     * 台标仓库中文件名与直播源写法不一致的频道
     *
     * 台标仓库按频道的规范名称命名，与直播源里的简称未必一致。
     */
    private val logoAliasMap = mapOf(
        "凤凰香港" to "凤凰卫视香港台",
    )

    /**
     * 归一化频道名称
     */
    fun normalize(raw: String): String {
        if (raw.isBlank()) return ""

        var value = Normalizer.normalize(raw, Normalizer.Form.NFKC).lowercase()

        value = cctvCnRegex.replace(value) { match ->
            val number = cnDigits[match.groupValues[1]] ?: match.groupValues[1].toIntOrNull()
            if (number == null) match.value else "cctv$number"
        }

        value = cctvRegex.replace(value) { match ->
            val plus = if (match.groupValues[2].isBlank()) "" else "plus"
            "cctv${match.groupValues[1]}$plus"
        }

        value = qualityRegex.replace(value, "")
        value = noiseRegex.replace(value, "")

        return value
    }

    /**
     * 频道核心标识
     *
     * 用于归一化后仍不完全一致的场景，例如「CCTV-5+赛事」与「CCTV5+-体育赛事」
     * 都归属到 cctv5plus。
     */
    fun coreKey(raw: String): String? {
        val normalized = normalize(raw)
        if (normalized.isBlank()) return null

        return cctvCoreRegex.find(normalized)?.value ?: normalized
    }

    /**
     * 判断两个名称是否指同一个频道
     */
    fun matches(left: String, right: String): Boolean {
        val normalizedLeft = normalize(left)
        val normalizedRight = normalize(right)

        if (normalizedLeft.isBlank() || normalizedRight.isBlank()) return false
        if (normalizedLeft == normalizedRight) return true

        // 只有在央视系列上才允许「前缀一致即视为同一频道」，
        // 避免把「浙江卫视」这类名称误并到别的频道
        val coreLeft = coreKey(left) ?: return false
        val coreRight = coreKey(right) ?: return false

        return coreLeft.startsWith("cctv") && coreLeft == coreRight
    }

    /**
     * 换算台标仓库中的文件名
     *
     * 台标仓库按电视台的规范写法命名，例如央视是「CCTV1.png」「CCTV5+.png」，
     * 而直播源里普遍写作「CCTV-1」「CCTV-5+」，直接按频道名拼接必然 404
     * （表现为除央视外都有台标）。这里把频道名换算成台标仓库的写法。
     *
     * 未命中任何规则时原样返回，因此「浙江卫视.png」这类本来一致的名称不受影响。
     */
    fun logoFileName(raw: String): String {
        val name = raw.trim()
        if (name.isBlank()) return ""

        logoAliasMap[name]?.let { return it }

        logoCctvRegex.find(normalize(name))?.let { match ->
            // 取捕获组而非整串，避免把「中央一台」归一化得到的尾字「台」带进文件名
            return "cctv${match.groupValues[1]}".replace("plus", "+").uppercase()
        }

        return name
    }
}
