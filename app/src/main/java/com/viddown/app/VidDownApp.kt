package com.viddown.app

import android.app.Application
import com.viddown.app.manager.EngineManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VidDownApp : Application() {

    // App-wide scope for kicking off engine initialization early
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Warm up the engine in the background so it's ready by the time
        // the user analyzes a URL. If it fails here, EngineManager will
        // retry (and surface the real error) on first actual use.
        appScope.launch {
            EngineManager.ensureInitialized(applicationContext)
        }
    }
}
