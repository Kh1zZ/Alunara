package com.alunara.core.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioFormatSelectorTest {
    @Test
    fun selectBest_rejectsVideoFormats() {
        val result =
            AudioFormatSelector.selectBest(
                listOf(
                    AudioFormatCandidate(itag = 18, mimeType = "video/mp4", bitrateBps = 500_000, isAudioOnly = false, hasDirectUrl = true),
                    AudioFormatCandidate(itag = 22, mimeType = "video/webm", bitrateBps = 700_000, isAudioOnly = false, hasDirectUrl = true),
                ),
            )
        assertNull(result)
    }

    @Test
    fun selectBest_prefersDirectUrlAndHighestCodecWithinCap() {
        val result =
            AudioFormatSelector.selectBest(
                listOf(
                    AudioFormatCandidate(itag = 140, mimeType = "audio/mp4; codecs=\"mp4a.40.2\"", bitrateBps = 128_000, isAudioOnly = true, hasDirectUrl = true),
                    AudioFormatCandidate(itag = 251, mimeType = "audio/webm; codecs=\"opus\"", bitrateBps = 160_000, isAudioOnly = true, hasDirectUrl = true),
                    AudioFormatCandidate(itag = 774, mimeType = "audio/webm; codecs=\"opus\"", bitrateBps = 256_000, isAudioOnly = true, hasDirectUrl = false),
                ),
            )
        assertEquals(251, result?.itag)
    }

    @Test
    fun selectBest_fallsBackToLowestAboveCapIfAllExceedCap() {
        val result =
            AudioFormatSelector.selectBest(
                listOf(
                    AudioFormatCandidate(itag = 991, mimeType = "audio/mp4", bitrateBps = 384_000, isAudioOnly = true, hasDirectUrl = true),
                    AudioFormatCandidate(itag = 992, mimeType = "audio/webm; codecs=\"opus\"", bitrateBps = 320_000, isAudioOnly = true, hasDirectUrl = true),
                ),
                maxPreferredBitrateBps = 256_000,
            )
        assertEquals(992, result?.itag)
    }

    @Test
    fun selectBest_withAudioQualityPresetsRespectsBitrateAndCodecTargets() {
        val mixed =
            listOf(
                AudioFormatCandidate(itag = 249, mimeType = "audio/webm; codecs=\"opus\"", bitrateBps = 52_000, isAudioOnly = true, hasDirectUrl = true),
                AudioFormatCandidate(itag = 250, mimeType = "audio/webm; codecs=\"opus\"", bitrateBps = 72_000, isAudioOnly = true, hasDirectUrl = true),
                AudioFormatCandidate(itag = 140, mimeType = "audio/mp4; codecs=\"mp4a.40.2\"", bitrateBps = 130_000, isAudioOnly = true, hasDirectUrl = true),
                AudioFormatCandidate(itag = 251, mimeType = "audio/webm; codecs=\"opus\"", bitrateBps = 160_000, isAudioOnly = true, hasDirectUrl = true),
                AudioFormatCandidate(itag = 141, mimeType = "audio/mp4; codecs=\"mp4a.40.2\"", bitrateBps = 256_000, isAudioOnly = true, hasDirectUrl = true),
            )
        assertEquals(250, AudioFormatSelector.selectBest(mixed, AudioQualityPreset.LOW)?.itag)
        assertEquals(251, AudioFormatSelector.selectBest(mixed, AudioQualityPreset.MEDIUM)?.itag)
        assertEquals(251, AudioFormatSelector.selectBest(mixed, AudioQualityPreset.AUTO)?.itag)
        assertEquals(141, AudioFormatSelector.selectBest(mixed, AudioQualityPreset.HIGH)?.itag)
    }
}
