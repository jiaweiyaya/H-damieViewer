package com.jiaweiya.hdamieviewer.pages

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontFamily

// ==================== Iwara 详情 API 专属数据模型 ====================

data class IwaraTagDetail(
    val id: String,
    val type: String?
)

data class IwaraDetailAvatar(
    val id: String?,
    val name: String?
)

data class IwaraDetailUser(
    val name: String?,
    val username: String?,
    val avatar: IwaraDetailAvatar?
)

data class IwaraVideoDetail(
    val id: String,
    val title: String,
    val body: String?, // 简介
    val numViews: Int?,
    val numLikes: Int?,
    val createdAt: String?,
    val tags: List<IwaraTagDetail>?,
    val user: IwaraDetailUser?,
    val fileUrl: String? // 直链解析接口
)

data class IwaraFileSource(
    val view: String?,
    val download: String?
)

data class IwaraVideoFormat(
    val name: String?,
    val src: IwaraFileSource?
)

// ==================== 网络数据拉取请求 ====================

suspend fun fetchVideoDetail(videoId: String): IwaraVideoDetail? {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.iwara.tv/video/$videoId")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val json = reader.readText()
                Gson().fromJson(json, IwaraVideoDetail::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

suspend fun fetchVideoFormats(fileUrl: String): List<IwaraVideoFormat> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(fileUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val json = reader.readText()
                val type = object : TypeToken<List<IwaraVideoFormat>>() {}.type
                Gson().fromJson<List<IwaraVideoFormat>>(json, type) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

// ==================== 播放器页面主体 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    videoId: String,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit
) {
    var videoDetail by remember { mutableStateOf<IwaraVideoDetail?>(null) }
    var videoUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var debugLog by remember { mutableStateOf("") }
    var showDebugDialog by remember { mutableStateOf(false) }

    LaunchedEffect(videoId) {
        isLoading = true
        debugLog = "--- 开始排查视频加载链路 ---\n"
        debugLog += "1. 视频 ID: $videoId\n"

        val detail = fetchVideoDetail(videoId)
        videoDetail = detail

        if (detail != null) {
            debugLog += "✅ 2. 获取详情成功!\n"
            debugLog += "   - 视频标题: ${detail.title}\n"
            debugLog += "   - 直链路由 fileUrl: ${detail.fileUrl}\n"

            if (!detail.fileUrl.isNullOrEmpty()) {
                debugLog += "3. 开始请求直链接口并获取原始数据...\n"

                // 抓取直链原始 JSON
                val rawJson = fetchRawFormatsJson(detail.fileUrl)
                debugLog += "   - 接口响应报文:\n$rawJson\n\n"

                if (rawJson != null && !rawJson.startsWith("HTTP 状态异常") && !rawJson.startsWith("连接异常")) {
                    try {
                        val type = object : TypeToken<List<IwaraVideoFormat>>() {}.type
                        val formats: List<IwaraVideoFormat> = Gson().fromJson(rawJson, type)

                        debugLog += "4. 格式解析：发现 ${formats.size} 个画质分支\n"
                        formats.forEach {
                            debugLog += "   - 分支 [${it.name}]: view=${it.src?.view}\n"
                        }

                        // 筛选最优画质
                        val bestFormat = formats.find { it.name == "Source" }
                            ?: formats.find { it.name == "540p" }
                            ?: formats.find { it.name == "360p" }
                            ?: formats.firstOrNull()

                        val rawUrl = bestFormat?.src?.view
                        videoUrl = if (rawUrl?.startsWith("//") == true) "https:$rawUrl" else rawUrl

                        debugLog += "   - 最终提取的播放直链: $videoUrl\n"
                    } catch (e: Exception) {
                        debugLog += "❌ 4. 格式解析失败 (可能接口返回结构已发生变动): ${e.localizedMessage}\n"
                    }
                } else {
                    debugLog += "❌ 3. 请求直链接口中断\n"
                }
            } else {
                debugLog += "❌ 2. 详情数据中 fileUrl 字段为空或未返回\n"
            }
        } else {
            debugLog += "❌ 1. 获取视频详情请求失败 (可能受限于代理或连接超时)\n"
        }
        isLoading = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (videoDetail != null) {
            val detail = videoDetail!!
            val videoTitle = detail.title
            val videoAuthor = detail.user?.name ?: detail.user?.username ?: "未知作者"
            val videoDesc = detail.body ?: "该视频暂无相关简介信息。"
            val videoTags = detail.tags?.map { it.id } ?: emptyList()
            val views = detail.numViews ?: 0
            val likes = detail.numLikes ?: 0
            val dateStr = detail.createdAt?.take(10) ?: "未知日期"

            // 头像路径拼接
            val avatarUrl = if (detail.user?.avatar != null) {
                "https://files.iwara.tv/image/avatar/${detail.user.avatar.id}/${detail.user.avatar.name}"
            } else ""

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. 顶部视频播放器区域 (16:9)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    if (!videoUrl.isNullOrEmpty()) {
                        // 渲染原生的 ExoPlayer 播放器
                        VideoPlayer(
                            videoUrl = videoUrl!!,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // 备用缓冲视图
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.DarkGray, Color.Black)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }

                    // 返回与主页操控栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                        }
                        IconButton(
                            onClick = onHomeClick,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(imageVector = Icons.Default.Home, contentDescription = "主页", tint = Color.White)
                        }
                    }
                }

                // 2. 作者栏配置
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 用户头像加载
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "用户头像",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = videoAuthor.take(1),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = videoAuthor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = "专栏创作者", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("关注", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp)
                    }
                }

                // 3. 视频标题、播放量与日期信息
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = videoTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
                    Text(
                        text = "${formatCount(views)}次播放  |  $dateStr",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. 简介折叠展开层 (自适应内容高度与展开按键避让)
                ExpandableDescription(
                    text = videoDesc,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 5. 快速操作按键行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    ActionButton(Icons.Default.TaskAlt, "快速打卡")
                    ActionButton(Icons.Default.FavoriteBorder, "加入喜欢")
                    ActionButton(Icons.Default.BookmarkBorder, "加入清单")
                    ActionButton(Icons.Default.Download, "下载")
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 6. 自适应两行高度并带有箭头的标签栏
                if (videoTags.isNotEmpty()) {
                    TagsSection(
                        tags = videoTags,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = { showDebugDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("查看并复制接口调试信息", fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        } else {
            // 加载失败占位
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Text("拉取视频数据失败，请检查连接", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (showDebugDialog) {
        AlertDialog(
            onDismissRequest = { showDebugDialog = false },
            title = { Text("接口调试控制台", fontWeight = FontWeight.Bold) },
            text = {
                Box(
                    modifier = Modifier
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer { // 允许用户在弹窗中双击、长按自由选择复制文本
                        Text(
                            text = debugLog,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDebugDialog = false }
                ) {
                    Text("关闭")
                }
            }
        )
    }
}

// ExoPlayer 核心渲染模块：自动加载直链并绑定生命周期防内存泄露
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(videoUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(videoUrl)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true // 自动播放视频
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release() // 释放内核资源
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true // 加载播放控制进度条和暂停按钮
            }
        },
        modifier = modifier
    )
}

// ==================== 辅助 UI 封装组件 ====================

// 1. 视频简介：3 行折叠/展开组件
@Composable
fun ExpandableDescription(text: String, modifier: Modifier = Modifier) {
    var isExpanded by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Text(
            text = text,
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { hasOverflow = it.hasVisualOverflow },
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
            modifier = Modifier
                .padding(end = if (hasOverflow && !isExpanded) 48.dp else 0.dp)
                .clickable { if (isExpanded) isExpanded = false }
        )

        if (hasOverflow && !isExpanded) {
            Text(
                text = "展开",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(MaterialTheme.colorScheme.background)
                    .clickable { isExpanded = true }
                    .padding(start = 4.dp)
            )
        }
    }
}

// 2. 快速操作行中的单个按键
@Composable
fun ActionButton(icon: ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable { }
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// 3. 标签栏：最大 2 行自适应展开折叠
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsSection(tags: List<String>, modifier: Modifier = Modifier) {
    var isExpanded by remember { mutableStateOf(false) }
    var hasMoreThanTwoRows by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = if (hasMoreThanTwoRows && !isExpanded) 36.dp else 0.dp)
                .then(if (!isExpanded) Modifier.heightIn(max = 84.dp) else Modifier)
                .onGloballyPositioned { coordinates ->
                    val heightDp = with(density) { coordinates.size.height.toDp() }
                    if (!isExpanded && heightDp >= 84.dp) {
                        hasMoreThanTwoRows = true
                    }
                }
        ) {
            tags.forEach { tag ->
                SuggestionChip(
                    onClick = {},
                    label = { Text(tag, fontSize = 12.sp) },
                    shape = RoundedCornerShape(8.dp),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
            }
        }

        if (hasMoreThanTwoRows) {
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "标签折叠控制",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// 获取直链 API 接口的原生响应报文
suspend fun fetchRawFormatsJson(fileUrl: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(fileUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                "HTTP 状态异常: ${connection.responseCode} (${connection.responseMessage})"
            }
        } catch (e: Exception) {
            "连接异常: ${e.localizedMessage ?: e.message ?: "未知网络错误"}"
        }
    }
}