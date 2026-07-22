package com.jiaweiya.hdamieviewer.iwara

import android.R
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// 下载任务状态实体
data class DownloadTaskState(
    val videoId: String,
    val videoTitle: String,
    val resolutionName: String,
    val downloadUrl: String,
    var progress: Float = 0f, // 0.0 ~ 1.0
    var speedBytesPerSec: Long = 0L,
    var isCompleted: Boolean = false,
    var isDownloading: Boolean = false
)

object IwaraDownloadManager {
    // 存储当前所有下载任务: Key 为 "$videoId-$resolutionName"
    val taskMap = mutableStateMapOf<String, DownloadTaskState>()

    private const val CHANNEL_ID = "iwara_download_channel"
    private const val CHANNEL_NAME = "Iwara 视频下载"

    // 格式化网速：保留 3 位小数，自动分配合适单位
    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0.000 B/s"
        val bytes = bytesPerSec.toDouble()
        val kib = 1024.0
        val mib = kib * 1024
        val gib = mib * 1024

        return when {
            bytes >= gib -> String.format(Locale.US, "%.3f GB/s", bytes / gib)
            bytes >= mib -> String.format(Locale.US, "%.3f MB/s", bytes / mib)
            bytes >= kib -> String.format(Locale.US, "%.3f KB/s", bytes / kib)
            else -> String.format(Locale.US, "%.3f B/s", bytes)
        }
    }

    // 初始化通知通道
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT // 👈 升级为普通通知，弹出横幅与声音提示
            ).apply {
                description = "显示 Iwara 视频后台下载进度"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    // 开始下载单个分辨率视频
    @SuppressLint("MissingPermission")
    fun startDownload(
        context: Context,
        scope: CoroutineScope,
        videoId: String,
        videoTitle: String,
        resolutionName: String,
        urlStr: String,
        authorName: String = "",
        videoTags: List<String> = emptyList(),
        videoDescription: String = ""
    ) {
        val taskKey = "$videoId-$resolutionName"
        val existingTask = taskMap[taskKey]

        // 如果已经在下载或已完成，不重复开启
        if (existingTask?.isDownloading == true || existingTask?.isCompleted == true) return

        val taskState = DownloadTaskState(
            videoId = videoId,
            videoTitle = videoTitle,
            resolutionName = resolutionName,
            downloadUrl = urlStr,
            isDownloading = true
        )
        taskMap[taskKey] = taskState

        createNotificationChannel(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = taskKey.hashCode()

        scope.launch(Dispatchers.IO) {
            try {
                val url = URL(urlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/122.0.0.0")
                connection.setRequestProperty("Referer", "https://www.iwara.tv/")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val totalLength = connection.contentLengthLong
                val inputStream = connection.inputStream

                // 使用视频专属子文件夹与纯分辨率文件名
                val videoDir = DownloadedVideoDb.getVideoDir(
                    context = context,
                    title = videoTitle,
                    id = videoId,
                    author = authorName,
                    tags = videoTags
                )

                val fileName = DownloadedVideoDb.buildVideoFileName(resolutionName)
                val targetFile = File(videoDir, fileName)

                val outputStream = FileOutputStream(targetFile)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalDownloaded = 0L

                var lastTime = System.currentTimeMillis()
                var lastDownloaded = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalDownloaded += bytesRead

                    val currentTime = System.currentTimeMillis()
                    val timeDiff = currentTime - lastTime

                    // 每 500ms 计算一次网速并刷新 UI 与通知栏
                    if (timeDiff >= 500) {
                        val bytesDiff = totalDownloaded - lastDownloaded
                        val speed = if (timeDiff > 0) (bytesDiff * 1000) / timeDiff else 0L

                        lastTime = currentTime
                        lastDownloaded = totalDownloaded

                        val progress = if (totalLength > 0) totalDownloaded.toFloat() / totalLength.toFloat() else 0f

                        // 1. 刷新内存 Task 状态 (触发 Compose 重绘)
                        taskMap[taskKey] = taskState.copy(
                            progress = progress,
                            speedBytesPerSec = speed,
                            isDownloading = true
                        )

                        // 2. 发送/更新系统通知栏
                        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(R.drawable.stat_sys_download)
                            .setContentTitle("正在下载: $videoTitle [$resolutionName]")
                            .setContentText("进度: ${(progress * 100).toInt()}%  |  网速: ${formatSpeed(speed)}")
                            .setProgress(100, (progress * 100).toInt(), false)
                            .setOngoing(true)
                            .setOnlyAlertOnce(true)
                            .build()

                        notificationManager.notify(notificationId, notification)
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                // 下载完成后写入数据库记录
                DownloadedVideoDb.insertRecord(
                    context = context,
                    record = DownloadedVideoRecord(
                        videoId = videoId,
                        resolution = resolutionName,
                        title = videoTitle,
                        authorName = authorName,
                        tags = videoTags,
                        description = videoDescription,
                        filePath = targetFile.absolutePath
                    )
                )

                // 下载完成更新
                taskMap[taskKey] = taskState.copy(
                    progress = 1.0f,
                    speedBytesPerSec = 0L,
                    isCompleted = true,
                    isDownloading = false
                )

                // 完成通知
                val completeNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.stat_sys_download_done)
                    .setContentTitle("下载完成: $videoTitle [$resolutionName]")
                    .setContentText("已下载到本地")
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .build()

                notificationManager.notify(notificationId, completeNotification)

            } catch (e: Exception) {
                e.printStackTrace()
                taskMap[taskKey] = taskState.copy(
                    isDownloading = false,
                    speedBytesPerSec = 0L
                )
            }
        }
    }
}