package com.ciphertun.aetherwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ciphertun.aetherwave.playback.PlayerManager
import com.ciphertun.aetherwave.ui.HomeScreen
import com.ciphertun.aetherwave.ui.theme.AetherwaveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PlayerManager.connect(applicationContext)

        setContent {
            AetherwaveTheme {
                HomeScreen()
            }
        }
    }
}
