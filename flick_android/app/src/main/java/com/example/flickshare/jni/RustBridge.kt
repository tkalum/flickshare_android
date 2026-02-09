
package com.example.flickshare.jni

object RustBridge {
    init {
        System.loadLibrary("flick_rust") // Match your Cargo.toml name
    }

    interface ProgressListener {
        fun onProgress(bytesDownloaded: Long)
    }
    external fun sendFile( port: Int, fd: Int, listener: ProgressListener): String
    external fun receiveFile(ip: String, port: Int, fd: Int, listener: ProgressListener): String
}