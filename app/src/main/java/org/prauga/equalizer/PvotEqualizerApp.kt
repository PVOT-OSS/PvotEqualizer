package org.prauga.equalizer

import android.app.Application
import org.prauga.equalizer.di.AppContainer

class PvotEqualizerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
