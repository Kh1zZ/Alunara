package com.alunara.app

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.os.StatFs
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.alunara.core.data.cache.CacheBudgetDecision
import com.alunara.core.data.cache.CacheBudgetManager
import okio.Path.Companion.toOkioPath
import kotlin.math.min

class AlunaraApplication :
    Application(),
    SingletonImageLoader.Factory {
    private val activityManager: ActivityManager? by lazy {
        getSystemService(ACTIVITY_SERVICE) as? ActivityManager
    }

    val cacheBudgetDecision: CacheBudgetDecision by lazy {
        val availableBytes =
            runCatching { StatFs(cacheDir.absolutePath).availableBytes }.getOrDefault(DEFAULT_ASSUMED_STORAGE_BYTES)
        val isLowRam = activityManager?.isLowRamDevice ?: false
        CacheBudgetManager.computeBudget(
            availableStorageBytes = availableBytes,
            lowMemoryMode = isLowRam,
        )
    }

    val container: AppContainer by lazy {
        AppContainer(
            context = this,
            cacheBudget = cacheBudgetDecision,
            isLowMemoryPressure = ::isUnderMemoryPressure,
        )
    }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.playbackLifecycleObserver)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            runCatching { SingletonImageLoader.get(this).memoryCache?.clear() }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val isLowRam = activityManager?.isLowRamDevice ?: false
        val memoryCapBytes = if (isLowRam) LOW_RAM_IMAGE_MEMORY_CACHE_BYTES else IMAGE_MEMORY_CACHE_BYTES
        val diskCapBytes = min(IMAGE_DISK_CACHE_BYTES, cacheBudgetDecision.dynamicCacheLimitBytes / 2L).coerceAtLeast(MIN_IMAGE_DISK_CACHE_BYTES)
        return ImageLoader
            .Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { container.okHttpClient }))
            }.memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizeBytes(memoryCapBytes)
                    .build()
            }.diskCache {
                DiskCache
                    .Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(diskCapBytes)
                    .build()
            }.build()
    }

    private fun isUnderMemoryPressure(): Boolean {
        val am = activityManager ?: return false
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.lowMemory
    }

    companion object {
        private const val IMAGE_MEMORY_CACHE_BYTES: Long = 12L * 1024L * 1024L
        private const val LOW_RAM_IMAGE_MEMORY_CACHE_BYTES: Long = 10L * 1024L * 1024L
        private const val IMAGE_DISK_CACHE_BYTES: Long = 32L * 1024L * 1024L
        private const val MIN_IMAGE_DISK_CACHE_BYTES: Long = 8L * 1024L * 1024L
        private const val DEFAULT_ASSUMED_STORAGE_BYTES: Long = 512L * 1024L * 1024L
    }
}
