package com.alunara.app

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okio.Path.Companion.toOkioPath

class AlunaraApplication :
    Application(),
    SingletonImageLoader.Factory {
    val container: AppContainer by lazy { AppContainer(this) }

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

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { container.okHttpClient }))
            }.memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizeBytes(IMAGE_MEMORY_CACHE_BYTES)
                    .build()
            }.diskCache {
                DiskCache
                    .Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                    .build()
            }.build()

    companion object {
        private const val IMAGE_MEMORY_CACHE_BYTES: Long = 12L * 1024L * 1024L
        private const val IMAGE_DISK_CACHE_BYTES: Long = 32L * 1024L * 1024L
    }
}
