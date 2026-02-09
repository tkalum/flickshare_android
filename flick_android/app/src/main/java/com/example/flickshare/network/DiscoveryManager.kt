//app/src/main/java/com/example/flickshare/netwrok/DiscoveryManager.kt
package com.example.flickshare.network

import android.content.ContentValues
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.provider.MediaStore
import android.util.Log
import com.example.flickshare.jni.RustBridge

class DiscoveryManager(
    private val context: Context,
    private val onUpdate: (String) -> Unit,
    private val onFinished: () -> Unit
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val SERVICE_TYPE = "_filetransfer._tcp."
    private var currentListener: NsdManager.DiscoveryListener? = null
    private var isDownloading = false

    fun start() {
        stop() // Clean up any old listeners
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) {
                // Immediately stop discovery once a peer is found to save resources
                stop()

                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        if (isDownloading) return

                        val ip = info.host.hostAddress ?: return
                        val port = info.port
                        val fileName = info.attributes["Filename"]?.let { String(it) } ?: "file_${System.currentTimeMillis()}"

                        val fd = createAndGetFd(fileName)
                        if (fd != -1) {
                            runDownload(ip, port, fd, fileName)
                        }
                    }
                    override fun onResolveFailed(s: NsdServiceInfo, e: Int) {
                        onUpdate("Resolve failed. Try again.")
                    }
                })
            }

            override fun onServiceLost(s: NsdServiceInfo) {}
            override fun onDiscoveryStarted(s: String) { onUpdate("Looking for devices...") }
            override fun onDiscoveryStopped(s: String) {}
            override fun onStartDiscoveryFailed(s: String, e: Int) { stop() }
            override fun onStopDiscoveryFailed(s: String, e: Int) {}
        }
        currentListener = listener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun runDownload(ip: String, port: Int, fd: Int, fileName: String) {
        isDownloading = true
        Thread {
            try {
                // Call Rust (Order: IP, Port, FD, Listener)
                RustBridge.receiveFile(ip, port, fd, object : RustBridge.ProgressListener {
                    override fun onProgress(bytes: Long) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onUpdate("Downloading $fileName: ${bytes / 1024} KB")
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e("JNI", "Download Error", e)
            } finally {
                isDownloading = false
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onUpdate("Download Complete!\nFile saved: Download/FlickShare/$fileName")
                    onFinished()
                }
            }
        }.start()
    }

    private fun createAndGetFd(fileName: String): Int {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/FlickShare")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            val pfd = context.contentResolver.openFileDescriptor(uri!!, "w")
            pfd?.detachFd() ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    fun stop() {
        try {
            currentListener?.let { nsdManager.stopServiceDiscovery(it) }
        } catch (e: Exception) {}
        currentListener = null
    }
}