package com.ciphertun.aetherwave

import android.app.Application
import com.ciphertun.aetherwave.backend.EmbeddedBackend
import com.ciphertun.aetherwave.data.LibraryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AetherwaveApp : Application() {
    private val backendScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        LibraryStore.init(this)
        EmbeddedBackend.initialize(this)
        backendScope.launch { EmbeddedBackend.start() }
    }
}
