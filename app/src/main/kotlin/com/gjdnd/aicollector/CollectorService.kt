package com.gjdnd.aicollector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import java.io.File
import java.util.concurrent.Executors

class CollectorService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var uploader: GitHubUploader
    private lateinit var connectivityManager: ConnectivityManager
    private var fileObserver: FileObserver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        uploader = GitHubUploader(applicationContext)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        startForegroundServiceNotification()
        startNetworkCallback()
        startDownloadObserver()
        rescanDownloads()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        rescanDownloads()
        return START_STICKY
    }

    override fun onDestroy() {
        fileObserver?.stopWatching()
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        executor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundServiceNotification() {
        val channelId = "ai_collector_sync"
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startDownloadObserver() {
        val downloadDir = getDownloadDir()
        val mask = FileObserver.CREATE or FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE
        fileObserver = object : FileObserver(downloadDir.absolutePath, mask) {
            override fun onEvent(event: Int, path: String?) {
                if (path.isNullOrBlank() || !path.endsWith(".md", ignoreCase = true)) {
                    return
                }
                enqueueUpload(File(downloadDir, path))
            }
        }.also { it.startWatching() }
    }

    private fun startNetworkCallback() {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                executor.execute { uploader.retryQueue() }
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        networkCallback = callback
    }

    private fun rescanDownloads() {
        val files = getDownloadDir().listFiles { file ->
            file.isFile && file.extension.equals("md", ignoreCase = true)
        }.orEmpty()

        files.forEach { enqueueUpload(it) }
    }

    private fun enqueueUpload(file: File) {
        executor.execute {
            if (waitForStableFile(file)) {
                uploader.uploadOrQueue(file)
            }
        }
    }

    private fun waitForStableFile(file: File): Boolean {
        repeat(6) {
            if (!file.exists()) {
                return false
            }

            val before = file.length()
            Thread.sleep(1_000)
            val after = file.length()
            if (before > 0 && before == after) {
                return true
            }
        }

        Log.w(TAG, "파일 쓰기 안정화 실패: ${file.name}")
        return false
    }

    private fun getDownloadDir(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    companion object {
        private const val TAG = "CollectorService"
        private const val NOTIFICATION_ID = 20260517
    }
}
