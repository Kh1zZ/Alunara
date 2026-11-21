package com.alunara.app.playback

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import com.alunara.core.domain.policy.FailureCategory
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Classifies Media3 [PlaybackException]s into domain [FailureCategory] values for [com.alunara.core.domain.policy.RetryPolicy].
 */
object PlaybackErrorClassifier {
    fun classify(error: PlaybackException): FailureCategory =
        classifyThrowable(root = error, errorCode = error.errorCode)

    @OptIn(UnstableApi::class)
    internal fun classifyThrowable(
        root: Throwable?,
        errorCode: Int,
    ): FailureCategory {
        var cause: Throwable? = root
        while (cause != null) {
            when (cause) {
                is HttpDataSource.InvalidResponseCodeException ->
                    return classifyHttpStatusCode(cause.responseCode)

                is SocketTimeoutException -> return FailureCategory.TRANSIENT_TIMEOUT
                is UnknownHostException -> return FailureCategory.OFFLINE
                is IOException -> {
                    if (cause !== root && cause !is HttpDataSource.HttpDataSourceException) {
                        return FailureCategory.OFFLINE
                    }
                }
            }
            cause = cause.cause
        }

        return when (errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> FailureCategory.TRANSIENT_TIMEOUT
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> FailureCategory.OFFLINE
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> FailureCategory.EXPIRED_SOURCE
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_TIMEOUT,
            -> FailureCategory.TRANSIENT_TIMEOUT
            else -> FailureCategory.CONTENT_UNAVAILABLE
        }
    }

    internal fun classifyHttpStatusCode(responseCode: Int): FailureCategory =
        when (responseCode) {
            401 -> FailureCategory.AUTH_INVALID
            403, 404, 410, 416 -> FailureCategory.EXPIRED_SOURCE
            429 -> FailureCategory.RATE_LIMITED
            in 500..599 -> FailureCategory.TRANSIENT_TIMEOUT
            else -> FailureCategory.CONTENT_UNAVAILABLE
        }
}
