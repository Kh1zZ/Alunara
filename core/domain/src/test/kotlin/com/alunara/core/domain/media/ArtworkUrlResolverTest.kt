package com.alunara.core.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkUrlResolverTest {
    @Test
    fun resolve_returnsNullForNullOrBlank() {
        assertNull(ArtworkUrlResolver.resolve(null, ArtworkTier.CARD))
        assertNull(ArtworkUrlResolver.resolve("   ", ArtworkTier.HERO))
    }

    @Test
    fun resolve_upgradesGoogleUserContentDimensionsPerTierAndPreservesCrop() {
        val songThumb = "https://lh3.googleusercontent.com/abc123=w120-h120-l90-rj"
        assertEquals(
            "https://lh3.googleusercontent.com/abc123=w256-h256-l90-rj",
            ArtworkUrlResolver.resolve(songThumb, ArtworkTier.THUMBNAIL),
        )
        assertEquals(
            "https://lh3.googleusercontent.com/abc123=w360-h360-l90-rj",
            ArtworkUrlResolver.resolve(songThumb, ArtworkTier.CARD),
        )
        assertEquals(
            "https://lh3.googleusercontent.com/abc123=w720-h720-l90-rj",
            ArtworkUrlResolver.resolve(songThumb, ArtworkTier.HERO),
        )

        val avatarThumb = "//yt3.ggpht.com/artistImg=s60-c-k-c0x00ffffff-no-rj"
        assertEquals(
            "https://yt3.ggpht.com/artistImg=w360-h360-c-l90-rj",
            ArtworkUrlResolver.resolve(avatarThumb, ArtworkTier.CARD),
        )
    }

    @Test
    fun resolve_upgradesYtImgThumbnailsSafelyPerTier() {
        val mqUrl = "https://i.ytimg.com/vi/video123/mqdefault.jpg?sqp=abc"
        assertEquals(
            "https://i.ytimg.com/vi/video123/hqdefault.jpg?sqp=abc",
            ArtworkUrlResolver.resolve(mqUrl, ArtworkTier.THUMBNAIL),
        )
        assertEquals(
            "https://i.ytimg.com/vi/video123/sddefault.jpg?sqp=abc",
            ArtworkUrlResolver.resolve(mqUrl, ArtworkTier.HERO),
        )

        val hqUrl = "https://i9.ytimg.com/vi/video123/hqdefault.jpg"
        assertEquals(
            hqUrl,
            ArtworkUrlResolver.resolve(hqUrl, ArtworkTier.CARD),
        )
        assertEquals(
            "https://i9.ytimg.com/vi/video123/sddefault.jpg",
            ArtworkUrlResolver.resolve(hqUrl, ArtworkTier.HERO),
        )
    }

    @Test
    fun resolve_leavesUnknownHostsUntouched() {
        val other = "https://cdn.example.com/cover.jpg?size=small"
        assertEquals(other, ArtworkUrlResolver.resolve(other, ArtworkTier.HERO))
    }
}
