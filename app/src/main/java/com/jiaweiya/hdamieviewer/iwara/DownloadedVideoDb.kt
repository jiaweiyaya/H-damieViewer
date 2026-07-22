package com.jiaweiya.hdamieviewer.iwara

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

// 已下载视频实体
data class DownloadedVideoRecord(
    val videoId: String,
    val resolution: String,
    val title: String,
    val authorName: String,
    val tags: List<String> = emptyList(),
    val description: String = "",
    val filePath: String = "",
    val downloadedTime: Long = System.currentTimeMillis()
)

object DownloadedVideoDb {
    private const val PREF_NAME = "HDAmieViewerDB"
    private const val KEY_RECORDS = "downloaded_video_records_json"
    private const val KEY_CUSTOM_PATH = "custom_download_path"

    // 1. 100% 可逆的文件系统非法字符转义算法
    fun encodeFsText(text: String): String {
        if (text.isEmpty()) return ""
        return text
            .replace("%", "%25")
            .replace("/", "%2F")
            .replace("\\", "%5C")
            .replace(":", "%3A")
            .replace("*", "%2A")
            .replace("?", "%3F")
            .replace("\"", "%22")
            .replace("<", "%3C")
            .replace(">", "%3E")
            .replace("|", "%7C")
            .replace("\r", "%0D")
            .replace("\n", "%0A")
            .replace("\t", "%09")
    }

    // 可逆解码算法，恢复原始标题与作者名
    fun decodeFsText(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return try {
            URLDecoder.decode(encoded, "UTF-8")
        } catch (e: Exception) {
            encoded
        }
    }

    // 2. Tags 标签的 GZIP 高压缩 + URL-Safe Base64 加密算法
    fun compressTags(tags: List<String>): String {
        if (tags.isEmpty()) return ""
        return try {
            val json = Gson().toJson(tags)
            val bos = ByteArrayOutputStream()
            GZIPOutputStream(bos).use { it.write(json.toByteArray(Charsets.UTF_8)) }
            Base64.encodeToString(
                bos.toByteArray(),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        } catch (e: Exception) {
            ""
        }
    }

    // Tags 标签无损解密解压还原算法
    fun decompressTags(encoded: String): List<String> {
        if (encoded.isBlank()) return emptyList()
        return try {
            val bytes = Base64.decode(encoded, Base64.URL_SAFE)
            val bis = ByteArrayInputStream(bytes)
            val json = GZIPInputStream(bis).bufferedReader(Charsets.UTF_8).readText()
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 默认路径：App 内部数据路径 download/video 目录
    fun getDefaultDownloadDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "download/video")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // 获取当前生效的根下载目录
    fun getActiveDownloadDir(context: Context): File {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val customPath = sp.getString(KEY_CUSTOM_PATH, "")
        if (!customPath.isNullOrEmpty()) {
            val file = File(customPath)
            if (!file.exists()) file.mkdirs()
            if (file.exists() && file.isDirectory) return file
        }
        return getDefaultDownloadDir(context)
    }

    // 获取一级目录：作者文件夹 (作者名)
    fun getAuthorDir(context: Context, author: String): File {
        val rootDir = getActiveDownloadDir(context)
        val safeAuthor = encodeFsText(author.ifEmpty { "未知作者" })
        val authorDir = File(rootDir, safeAuthor)
        if (!authorDir.exists()) authorDir.mkdirs()
        return authorDir
    }

    // 获取二级目录：视频专属文件夹 (标题__ID_视频ID__TAGS_加密Tags)
    fun getVideoDir(context: Context, title: String, id: String, author: String, tags: List<String>): File {
        val authorDir = getAuthorDir(context, author)
        val safeTitle = encodeFsText(title.ifEmpty { "未命名视频" })
        val compressedTags = compressTags(tags)
        val tagsPart = if (compressedTags.isNotEmpty()) "__TAGS_${compressedTags}" else ""

        val videoFolderName = "${safeTitle}__ID_${id}${tagsPart}"
        val videoDir = File(authorDir, videoFolderName)
        if (!videoDir.exists()) videoDir.mkdirs()
        return videoDir
    }

    // 构建文件夹内部的视频文件名 (纯分辨率命名: Source.mp4 / 1080.mp4)
    fun buildVideoFileName(resolution: String): String {
        val safeRes = encodeFsText(resolution.ifEmpty { "Source" })
        return "${safeRes}.mp4"
    }

    fun saveCustomDownloadPath(context: Context, path: String) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_CUSTOM_PATH, path).apply()
    }

    fun getAllRecords(context: Context): List<DownloadedVideoRecord> {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = sp.getString(KEY_RECORDS, "") ?: ""
        if (json.isEmpty()) return emptyList()
        return try {
            val type = object : TypeToken<List<DownloadedVideoRecord>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAllRecords(context: Context, list: List<DownloadedVideoRecord>) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(list)
        sp.edit().putString(KEY_RECORDS, json).apply()
    }

    fun insertRecord(context: Context, record: DownloadedVideoRecord) {
        val current = getAllRecords(context).toMutableList()
        current.removeAll { it.videoId == record.videoId && it.resolution == record.resolution }
        current.add(0, record)
        saveAllRecords(context, current)
    }

    // 重新盘点所有“一级作者目录 -> 二级视频目录 -> 视频文件”并解密还原元数据
    fun rescanDirectory(context: Context): List<DownloadedVideoRecord> {
        val rootDir = getActiveDownloadDir(context)
        val dbRecords = getAllRecords(context).associateBy { "${it.videoId}-${it.resolution}" }
        val updatedMap = mutableMapOf<String, DownloadedVideoRecord>()

        val folderRegex = Regex("^(.*)__ID_(.*?)(?:__TAGS_(.*))?$")

        // 1. 扫描一级目录：作者文件夹
        val authorDirs = rootDir.listFiles { file -> file.isDirectory } ?: emptyArray()

        for (authorDir in authorDirs) {
            val authorName = decodeFsText(authorDir.name)

            // 2. 扫描二级目录：视频文件夹
            val videoDirs = authorDir.listFiles { file -> file.isDirectory } ?: emptyArray()

            for (videoDir in videoDirs) {
                val match = folderRegex.find(videoDir.name)
                if (match != null) {
                    val (encodedTitle, id, compressedTags) = match.destructured
                    val title = decodeFsText(encodedTitle)
                    val tags = decompressTags(compressedTags)

                    // 3. 扫描视频文件夹内的所有 mp4 文件
                    val mp4Files = videoDir.listFiles { file -> file.isFile && file.extension.lowercase() == "mp4" } ?: emptyArray()

                    for (mp4File in mp4Files) {
                        val resolution = decodeFsText(mp4File.nameWithoutExtension)
                        val key = "$id-$resolution"

                        val oldRecord = dbRecords[key]
                        if (oldRecord != null) {
                            updatedMap[key] = oldRecord.copy(
                                filePath = mp4File.absolutePath,
                                title = title,
                                authorName = authorName,
                                tags = if (oldRecord.tags.isNotEmpty()) oldRecord.tags else tags
                            )
                        } else {
                            // 导入别人拷贝进来的离线视频：根据文件夹解密无损还原标题、作者与标签！
                            updatedMap[key] = DownloadedVideoRecord(
                                videoId = id,
                                resolution = resolution,
                                title = title,
                                authorName = authorName,
                                tags = tags,
                                description = "",
                                filePath = mp4File.absolutePath,
                                downloadedTime = mp4File.lastModified()
                            )
                        }
                    }
                }
            }
        }

        // 4. 兼容兜底：若根目录下有旧格式平铺的视频或文件夹，也一并向下兼容识别
        val oldSingleFiles = rootDir.listFiles { file -> file.isFile && file.extension.lowercase() == "mp4" } ?: emptyArray()
        val oldSingleRegex = Regex("^(.*)__ID_(.*)__RES_(.*)__BY_(.*)\\.mp4$")
        for (file in oldSingleFiles) {
            val match = oldSingleRegex.find(file.name)
            if (match != null) {
                val (title, id, res, author) = match.destructured
                val key = "$id-$res"
                if (!updatedMap.containsKey(key)) {
                    val oldRecord = dbRecords[key]
                    updatedMap[key] = oldRecord?.copy(filePath = file.absolutePath) ?: DownloadedVideoRecord(
                        videoId = id, resolution = res, title = title, authorName = author,
                        filePath = file.absolutePath, downloadedTime = file.lastModified()
                    )
                }
            }
        }

        val resultList = updatedMap.values.toList().sortedByDescending { it.downloadedTime }
        saveAllRecords(context, resultList)
        return resultList
    }

    // 切换目录时递归平滑移动包含两级子目录的整体结构
    fun moveFilesToNewDir(context: Context, oldDir: File, newDir: File) {
        if (!newDir.exists()) newDir.mkdirs()
        if (!oldDir.exists() || oldDir.absolutePath == newDir.absolutePath) return

        try {
            oldDir.copyRecursively(newDir, overwrite = true)
            oldDir.deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        rescanDirectory(context)
    }
}