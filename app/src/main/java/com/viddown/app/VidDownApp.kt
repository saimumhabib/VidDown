package com.viddown.app

import android.app.Application

class VidDownApp : Application() {
    lateinit var controller: DownloadController
        private set

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        controller = DownloadController(this)
    }
}
