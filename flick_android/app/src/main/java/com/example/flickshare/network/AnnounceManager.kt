//app/src/main/java/com/example/flickshare/netwrok/AnnouceManager.kt
package com.example.flickshare.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.example.flickshare.jni.RustBridge

class AnnounceManager(
    private val context: Context,
    private val onUpdate: (String) -> Unit,
    private val onFinished: () -> Unit
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    fun start(fileName: String, port: Int, fd: Int) {

        val fileSize: Long = try {
            // Use the ParcelFileDescriptor to get the actual size of the file
            val pfd = android.os.ParcelFileDescriptor.adoptFd(fd)
            val size = pfd.statSize
            // Important: we 'detach' again so we don't close the FD yet
            pfd.detachFd()
            size
        } catch (e: Exception) {
            Log.e("NSD", "Failed to get file size", e)
            0L
        }

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "FlickShare_${android.os.Build.MODEL}"
            serviceType = "_filetransfer._tcp."
            setPort(port)
            setAttribute("Filename", fileName)
            setAttribute("Filesize", fileSize.toString())
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                onUpdate("Waiting for connection...")
                runRustServer(port, fd, fileName)
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, err: Int) {
                onUpdate("Announcement failed")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, err: Int) {}
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    private fun runRustServer(port: Int, fd: Int, fileName: String) {
        Thread {
            try {
                RustBridge.sendFile(port, fd, object : RustBridge.ProgressListener {
                    override fun onProgress(bytes: Long) {
                        onUpdate("Sending $fileName: ${bytes / 1024} KB")
                    }
                })
            } catch (e: Exception) {
                Log.e("JNI", "Send Error", e)
            } finally {
                stop()
                onUpdate("Finished sending $fileName")
                onFinished()
            }
        }.start()
    }

    fun stop() {
        try {
            registrationListener?.let { nsdManager.unregisterService(it) }
        } catch (e: Exception) {}
        registrationListener = null
    }
}