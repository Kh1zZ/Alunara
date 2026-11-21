package com.alunara.app

import androidx.media3.common.PlaybackException
import com.alunara.app.playback.PlaybackErrorClassifier
import com.alunara.core.domain.policy.FailureCategory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class PlaybackErrorClassifierTest {
    @Test
    fun classifyHttpStatusCode_mapsExpectedStatuses() {
        assertEquals(FailureCategory.EXPIRED_SOURCE, PlaybackErrorClassifier.classifyHttpStatusCode(403))
        assertEquals(FailureCategory.EXPIRED_SOURCE, PlaybackErrorClassifier.classifyHttpStatusCode(410))
        assertEquals(FailureCategory.RATE_LIMITED, PlaybackErrorClassifier.classifyHttpStatusCode(429))
        assertEquals(FailureCategory.AUTH_INVALID, PlaybackErrorClassifier.classifyHttpStatusCode(401))
        assertEquals(FailureCategory.TRANSIENT_TIMEOUT, PlaybackErrorClassifier.classifyHttpStatusCode(503))
        assertEquals(FailureCategory.CONTENT_UNAVAILABLE, PlaybackErrorClassifier.classifyHttpStatusCode(400))
    }

    @Test
    fun classifyThrowable_mapsNetworkTimeoutsOfflineAndErrorCodes() {
        val timeoutWrapper = RuntimeException(SocketTimeoutException("Read timed out"))
        val offlineWrapper = RuntimeException(UnknownHostException("music.youtube.com"))
        val ioWrapper = RuntimeException(IOException("Network unreachable"))

        assertEquals(
            FailureCategory.TRANSIENT_TIMEOUT,
            PlaybackErrorClassifier.classifyThrowable(timeoutWrapper, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT),
        )
        assertEquals(
            FailureCategory.OFFLINE,
            PlaybackErrorClassifier.classifyThrowable(offlineWrapper, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED),
        )
        assertEquals(
            FailureCategory.OFFLINE,
            PlaybackErrorClassifier.classifyThrowable(ioWrapper, PlaybackException.ERROR_CODE_IO_UNSPECIFIED),
        )
        assertEquals(
            FailureCategory.EXPIRED_SOURCE,
            PlaybackErrorClassifier.classifyThrowable(null, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS),
        )
        assertEquals(
            FailureCategory.CONTENT_UNAVAILABLE,
            PlaybackErrorClassifier.classifyThrowable(null, PlaybackException.ERROR_CODE_DECODING_FAILED),
        )
    }
}
