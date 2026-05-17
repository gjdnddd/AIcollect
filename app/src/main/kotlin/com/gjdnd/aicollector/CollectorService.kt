package com.gjdnd.aicollector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import java.io.File
import java.util.concurrent.Executors

class CollectorService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var uploader: GitHubUploader
    private lateinit var logger: UploadLogger

    override fun onCreate() {
        super.onCreate()
        uploader = GitHubUploader(applicationContext)
        logger = UploadLogger(applicationContext)
        startForegroundServiceNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val filePath = intent?.getStringExtra(EXTRA_FILE_PATH)
        executor.execute {
            try {
                uploader.retryQueue()
                if (filePath.isNullOrBlank()) {
                    rescanDownloads()
                } else {
                    processFile(File(filePath))
                }
                logger.append("수집 실행 완료")
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
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

    private fun rescanDownloads() {
        val files = getDownloadDir().listFiles { file ->
            file.isFile && file.extension.equals("md", ignoreCase = true)
        }.orEmpty()

        logger.append("Downloads 수집 실행: ${files.size}개 발견")
        files.forEach { processFile(it) }
    }

    private fun processFile(file: File) {
        if (waitForStableFile(file)) {
            uploader.uploadOrQueue(file)
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
        logger.append("파일 쓰기 안정화 실패: ${file.name}")
        return false
    }

    private fun getDownloadDir(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
        private const val TAG = "CollectorService"
        private const val NOTIFICATION_ID = 20260517
    }
}
