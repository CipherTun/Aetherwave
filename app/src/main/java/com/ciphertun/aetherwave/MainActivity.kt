package com.ciphertun.aetherwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.ciphertun.aetherwave.data.LibraryStore
import com.ciphertun.aetherwave.playback.PlayerManager
import com.ciphertun.aetherwave.ui.HomeScreen
import com.ciphertun.aetherwave.ui.theme.AetherwaveTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val importAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch { LibraryStore.importAudio(uri) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PlayerManager.connect(applicationContext)

        setContent {
            AetherwaveTheme {
                HomeScreen(onImportAudio = { importAudio.launch(arrayOf("audio/*")) })
            }
        }
    }
}
