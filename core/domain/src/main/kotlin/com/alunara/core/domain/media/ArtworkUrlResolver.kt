package com.alunara.core.domain.media

enum class ArtworkTier(
    val maxPx: Int,
) {
    THUMBNAIL(256),
    CARD(360),
    HERO(720),
}

/**
 * Upgrades low-resolution YouTube / Googleusercontent artwork URLs (often 60px–120px)
 * to crisp dimensions tailored to 10-foot Android TV cards while staying memory-safe
 * for 1 GB RAM hardware.
 */
object ArtworkUrlResolver {
    private val googleHosts =
        setOf(
            "lh3.googleusercontent.com",
            "yt3.ggpht.com",
            "yt3.googleusercontent.com",
        )
    private val googleSizeRegex = Regex("""=(?:w\d+-h\d+|s\d+)([^\s?]*)$""", RegexOption.IGNORE_CASE)
    private val ytImgHostRegex = Regex("""^i\d*\.ytimg\.com$""", RegexOption.IGNORE_CASE)
    private val ytImgThumbRegex = Regex("""/(?:default|mqdefault|hqdefault)\.jpg(\?.*)?$""", RegexOption.IGNORE_CASE)
    private val hostRegex = Regex("""^https?://([^/:]+)""", RegexOption.IGNORE_CASE)

    fun resolve(
        url: String?,
        tier: ArtworkTier,
    ): String? {
        val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalized = if (raw.startsWith("//")) "https:$raw" else raw
        val host = hostRegex.find(normalized)?.groupValues?.getOrNull(1)?.lowercase() ?: return normalized

        if (host in googleHosts || host.endsWith(".googleusercontent.com")) {
            val match = googleSizeRegex.find(normalized)
            val cropSuffix = if (match != null && "-c" in match.groupValues[1].lowercase()) "-c" else ""
            val targetSuffix = "=w${tier.maxPx}-h${tier.maxPx}$cropSuffix-l90-rj"
            return if (match != null) {
                normalized.substring(0, match.range.first) + targetSuffix
            } else {
                normalized + targetSuffix
            }
        }

        if (ytImgHostRegex.matches(host)) {
            val targetFile =
                when (tier) {
                    ArtworkTier.THUMBNAIL, ArtworkTier.CARD -> "/hqdefault.jpg"
                    ArtworkTier.HERO -> "/sddefault.jpg"
                }
            val match = ytImgThumbRegex.find(normalized) ?: return normalized
            val matchedText = match.value
            val query = match.groupValues.getOrElse(1) { "" }
            val currentFile = matchedText.removeSuffix(query).lowercase()
            val shouldUpgrade =
                when (tier) {
                    ArtworkTier.THUMBNAIL, ArtworkTier.CARD -> currentFile == "/default.jpg" || currentFile == "/mqdefault.jpg"
                    ArtworkTier.HERO -> currentFile == "/default.jpg" || currentFile == "/mqdefault.jpg" || currentFile == "/hqdefault.jpg"
                }
            if (shouldUpgrade) {
                return normalized.substring(0, match.range.first) + targetFile
            }
        }

        return normalized
    }
}
