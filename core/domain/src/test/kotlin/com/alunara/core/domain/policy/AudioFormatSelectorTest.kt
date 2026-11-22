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
    fun selectBest_strictlyPrefersAacOverOpusEvenIfNonDirectOrHigherCap() {
        val result =
            AudioFormatSelector.selectBest(
                listOf(
                    AudioFormatCandidate(itag = 251, mimeType = "audio/webm; codecs=\"opus\"", bitrateBps = 160_000, isAudioOnly = true, hasDirectUrl = true),
                    AudioFormatCandidate(itag = 140, mimeType = "audio/mp4; codecs=\"mp4a.40.2\"", bitrateBps = 128_000, isAudioOnly = true, hasDirectUrl = true),
                    AudioFormatCandidate(itag = 141, mimeType = "audio/mp4; codecs=\"mp4a.40.2\"", bitrateBps = 256_000, isAudioOnly = true, hasDirectUrl = false),
                ),
            )
        assertEquals(140, result?.itag)

        val cipherAacVsDirectOpus =
            AudioFormatSelector.selectBest(
                listOf(
                    AudioFormatCandidate(itag = 251, mimeType = "audio/webm; codecs=\"opus\"", bitrateBps = 160_000, isAudioOnly = true, hasDirectUrl = true),
                    AudioFormatCandidate(itag = 140, mimeType = "audio/mp4; codecs=\"mp4a.40.2\"", bitrateBps = 128_000, isAudioOnly = true, hasDirectUrl = false),
                ),
            )
        assertEquals(140, cipherAacVsDirectOpus?.itag)
    }

    @Test
    fun selectBest_fallsBackToOpusOnlyWhenNoAacFormatExists() {
        val opusOnly =
            listOf(
                AudioFormatCandidate(itag = 249, mimeType = "audio/webm; codecs=\"opus\"", bitrateBps = 52_000, isAudioOnly = true, hasDirectUrl = true),
                AudioFormatCandidate(itag = 250, mimeType = "audio/webm; codecs=\"opus\"", bitrateBps = 72_000, isAudioOnly = true, hasDirectUrl = true),
                AudioFormatCandidate(itag = 251, mimeType = "audio/webm; codecs=\"opus\"", bitrateBps = 160_000, isAudioOnly = true, hasDirectUrl = true),
            )
        assertEquals(250, AudioFormatSelector.selectBest(opusOnly, AudioQualityPreset.LOW)?.itag)
        assertEquals(251, AudioFormatSelector.selectBest(opusOnly, AudioQualityPreset.MEDIUM)?.itag)
        assertEquals(251, AudioFormatSelector.selectBest(opusOnly, AudioQualityPreset.AUTO)?.itag)
        assertEquals(251, AudioFormatSelector.selectBest(opusOnly, AudioQualityPreset.HIGH)?.itag)
    }

    @Test
    fun selectBest_allQualityPresetsPreferAacFirstAndHighUsesHighestBitrate() {
        val mixed =
            listOf(
                AudioFormatCandidate(itag = 249, mimeType = "audio/webm; codecs=\"opus\"", bitrateBps = 52_000, isAudioOnly = true, hasDirectUrl = true),
                AudioFormatCandidate(itag = 250, mimeType = "audio/webm; codecs=\"opus\"", bitrateBps = 72_000, isAudioOnly = true, hasDirectUrl = true),
                AudioFormatCandidate(itag = 140, mimeType = "audio/mp4; codecs=\"mp4a.40.2\"", bitrateBps = 130_000, isAudioOnly = true, hasDirectUrl = true),
                AudioFormatCandidate(itag = 251, mimeType = "audio/webm; codecs=\"opus\"", bitrateBps = 160_000, isAudioOnly = true, hasDirectUrl = true),
                AudioFormatCandidate(itag = 141, mimeType = "audio/mp4; codecs=\"mp4a.40.2\"", bitrateBps = 256_000, isAudioOnly = true, hasDirectUrl = true),
            )
        assertEquals(140, AudioFormatSelector.selectBest(mixed, AudioQualityPreset.LOW)?.itag)
        assertEquals(140, AudioFormatSelector.selectBest(mixed, AudioQualityPreset.MEDIUM)?.itag)
        assertEquals(140, AudioFormatSelector.selectBest(mixed, AudioQualityPreset.AUTO)?.itag)
        assertEquals(141, AudioFormatSelector.selectBest(mixed, AudioQualityPreset.HIGH)?.itag)

        val withLowAac =
            listOf(
                AudioFormatCandidate(itag = 139, mimeType = "audio/mp4; codecs=\"mp4a.40.5\"", bitrateBps = 48_000, isAudioOnly = true, hasDirectUrl = true),
            ) + mixed
        assertEquals(139, AudioFormatSelector.selectBest(withLowAac, AudioQualityPreset.LOW)?.itag)
    }
}
