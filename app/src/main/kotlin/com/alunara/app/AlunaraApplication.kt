package com.alunara.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner

class AlunaraApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.playbackLifecycleController)
    }
}
