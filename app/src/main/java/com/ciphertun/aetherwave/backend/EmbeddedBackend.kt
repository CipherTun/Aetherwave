package com.ciphertun.aetherwave.backend

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object EmbeddedBackend {
    private const val PORT = 17843
    private var context: Context? = null
    private var process: Process? = null
    @Volatile private var ready = false

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    fun baseUrl(): String = "http://127.0.0.1:$PORT"

    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (ready && healthCheck()) return@withContext true
        val ctx = context ?: return@withContext false
        runCatching {
            val binary = extractBinary(ctx)
            process?.takeIf { it.isAlive } ?: EmbeddedBackendProcess
                .start(binary, "127.0.0.1:$PORT")
                .also { process = it }

            repeat(60) {
                if (healthCheck()) {
                    ready = true
                    return@withContext true
                }
                if (process?.isAlive == false) return@withContext false
                delay(250)
            }
            false
        }.getOrDefault(false)
    }

    suspend fun awaitReady(): Boolean {
        if (ready && healthCheck()) return true
        return start()
    }

    private fun extractBinary(ctx: Context): File {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val supported = setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        val selected = if (abi in supported) abi else "arm64-v8a"
        val directory = File(ctx.filesDir, "embedded-backend/$selected").also { it.mkdirs() }
        val output = File(directory, "aetherwave-server")
        val versioned = File(directory, "version-1")
        if (!output.exists() || !versioned.exists()) {
            ctx.assets.open("backend/$selected/aetherwave-server").use { input ->
                output.outputStream().use { out -> input.copyTo(out) }
            }
            output.setExecutable(true, false)
            versioned.writeText("1")
        }
        return output
    }

    private fun healthCheck(): Boolean = runCatching {
        val connection = URL("$baseUrl()/health").openConnection() as HttpURLConnection
        connection.connectTimeout = 600
        connection.readTimeout = 600
        connection.requestMethod = "GET"
        connection.responseCode in 200..299
    }.getOrDefault(false)
}
