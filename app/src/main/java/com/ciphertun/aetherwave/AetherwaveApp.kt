package com.ciphertun.aetherwave

import android.app.Application
import com.ciphertun.aetherwave.data.LibraryStore

class AetherwaveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LibraryStore.init(this)
    }
}
