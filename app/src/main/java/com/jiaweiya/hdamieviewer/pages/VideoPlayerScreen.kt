package com.jiaweiya.hdamieviewer.pages

import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

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
    val context = LocalContext.current

    val sharedPrefs = remember { context.getSharedPreferences("HDAmieViewerDB", Context.MODE_PRIVATE) }
    val playerType = sharedPrefs.getInt("player_type", 0) // 去除 remember，保证每次进入实时读取最新

    // 【核心修复 1】：将 ExoPlayer 的生命周期提升到最顶层管理，脱离分支，使其在旋转重绘时不受干扰
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    // 监听播放链接，有直链时自动装载并准备播放
    LaunchedEffect(videoUrl) {
        if (!videoUrl.isNullOrEmpty()) {
            val mediaItem = MediaItem.fromUri(videoUrl!!)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    // 只有当整个页面彻底关闭销毁时，才释放播放器资源
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    var debugLog by remember { mutableStateOf("") }
    var showDebugDialog by remember { mutableStateOf(false) }

    // 获取当前窗口的 Activity 与屏幕旋转方向状态
    val activity = remember(context) { context as? android.app.Activity }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // 智能劫持物理返回键：如果在横屏，按返回键先退出横屏；如果在竖屏，则正常 pop 返回主页
    BackHandler {
        activity?.let { act ->
            if (act.requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val controller = androidx.core.view.WindowCompat.getInsetsController(act.window, act.window.decorView)
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) // 显示系统状态栏
            } else {
                onBackClick()
            }
        } ?: onBackClick()
    }

    LaunchedEffect(videoId) {
        isLoading = true
        debugLog = "--- 开始排查视频加载链路 ---\n"
        debugLog += "1. 视频 ID: $videoId, 播放器类型代码: $playerType\n"

        val detail = fetchVideoDetail(videoId)
        videoDetail = detail

        if (detail != null) {
            debugLog += "✅ 2. 获取详情成功!\n"
            debugLog += "   - 视频标题: ${detail.title}\n"
            debugLog += "   - 直链路由 fileUrl: ${detail.fileUrl}\n"

            if (!detail.fileUrl.isNullOrEmpty()) {
                debugLog += "3. 开始请求直链接口并获取原始数据...\n"

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

            val avatarUrl = if (detail.user?.avatar != null) {
                "https://files.iwara.tv/image/avatar/${detail.user.avatar.id}/${detail.user.avatar.name}"
            } else ""

            // 【自适应重绘】：如果是横屏模式，强制不展示任何图文列表，让播放器占满屏幕
            if (isLandscape) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    if (!videoUrl.isNullOrEmpty()) {
                        MpvMinimalPlayer(
                            exoPlayer = exoPlayer,
                            videoUrl = videoUrl!!,
                            onBackClick = {
                                // 横屏下按返回，恢复竖屏并重置系统状态栏
                                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                activity?.window?.let { win ->
                                    androidx.core.view.WindowCompat.getInsetsController(win, win.decorView).show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                                }
                            },
                            onHomeClick = onHomeClick,
                            onFullscreenClick = {
                                // 点击右下角退出全屏，恢复竖屏
                                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                activity?.window?.let { win ->
                                    androidx.core.view.WindowCompat.getInsetsController(win, win.decorView).show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                // 正常的竖屏 Scaffold 滚动列表
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. 顶部视频播放器区域 (16:9，带状态栏安全避让)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .statusBarsPadding() // 追加此行，修复“状态栏挡住画面”的问题
                            .aspectRatio(16f / 9f)
                    ) {
                        if (!videoUrl.isNullOrEmpty()) {
                            when (playerType) {
                                1 -> MpvMinimalPlayer(
                                    exoPlayer = exoPlayer,
                                    videoUrl = videoUrl!!,
                                    onBackClick = onBackClick,
                                    onHomeClick = onHomeClick,
                                    onFullscreenClick = {
                                        // 点击右下角全屏，旋转为横屏并隐藏系统栏
                                        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        activity?.window?.let { win ->
                                            val controller = androidx.core.view.WindowCompat.getInsetsController(win, win.decorView)
                                            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                                            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                                2 -> NativeMediaPlayer(videoUrl = videoUrl!!, modifier = Modifier.fillMaxSize())
                                else -> VideoPlayer(exoPlayer = exoPlayer, modifier = Modifier.fillMaxSize())
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }

                        // 只有在非“极简美化版 (MpvPlayer)”时，才在外层堆叠常驻的控制顶栏
                        if (playerType != 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
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

                    ExpandableDescription(
                        text = videoDesc,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

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
            }
        } else {
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
                    SelectionContainer {
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

// ExoPlayer 核心渲染模块：接受外部传入的播放器，不再自行创建/销毁
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(exoPlayer: ExoPlayer, modifier: Modifier = Modifier) {
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
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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

// ==================== 播放器扩展内核 ====================

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MpvMinimalPlayer(
    exoPlayer: ExoPlayer,
    videoUrl: String,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 播放进度与控制栏显示状态
    var currentPosition by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var isControlsVisible by remember { mutableStateOf(true) }

    // 进度轮询监听
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            bufferedPosition = exoPlayer.bufferedPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            kotlinx.coroutines.delay(250) // 250 毫秒高频刷新细进度条，获得最佳拖动回馈
        }
    }

    // 自动退场：播放状态下 3 秒无操作自动缩回控制栏
    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying) {
            kotlinx.coroutines.delay(3000)
            isControlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isControlsVisible = !isControlsVisible // 单击整个视频区域自由开关控制台
            }
    ) {
        // 底层视频画面
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // 完全关闭原生臃肿的大控制台
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Bilibili 风格的高级 Compose 自定义悬浮控制面板
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // 1. 顶部控制栏（返回、主页、附带由上至下的渐变阴影）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = onHomeClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Home, contentDescription = "主页", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                // 2. 底部控制栏（小巧的播放暂停键、超细 Bilibili 进度条、时间比例、全屏键、下至上渐变阴影）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左下角：紧凑的播放暂停控制
                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            isPlaying = exoPlayer.isPlaying
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "播放暂停",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

// 中间：【完美还原 Bilibili】超细轨加极小滑块的进度条
                    var widthPx by remember { mutableIntStateOf(0) } // 实时测量滚动条物理宽度
                    val density = LocalDensity.current

                    // 【核心修复】：重新补回这个外层触摸容器！它负责用 weight(1f) 挤压均分空间，提供舒适的触控高度，并将 2.dp 轨道垂直居中
                    Box(
                        modifier = Modifier
                            .weight(1f) // 1. 均分剩余空间，绝不挤压旁边的文字和全屏键
                            .height(32.dp) // 2. 提供 32.dp 的舒适垂直触控高度
                            .onGloballyPositioned { widthPx = it.size.width }
                            // 3. 绑定点击跳转进度手势
                            .pointerInput(duration) {
                                detectTapGestures { offset ->
                                    if (widthPx > 0 && duration > 0) {
                                        val fraction = (offset.x / widthPx).coerceIn(0f, 1f)
                                        val targetPos = (fraction * duration).toLong()
                                        exoPlayer.seekTo(targetPos)
                                        currentPosition = targetPos
                                    }
                                }
                            }
                            // 4. 绑定左右滑动拖拽手势
                            .pointerInput(duration) {
                                detectHorizontalDragGestures { change, _ ->
                                    change.consume()
                                    if (widthPx > 0 && duration > 0) {
                                        val fraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                                        val targetPos = (fraction * duration).toLong()
                                        exoPlayer.seekTo(targetPos)
                                        currentPosition = targetPos
                                    }
                                }
                            },
                        contentAlignment = Alignment.CenterStart // 5. 让内部的 2.dp 轨道在 32.dp 容器内完美垂直居中！
                    ) {
                        val fraction = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                        val bufferedFraction = if (duration > 0) (bufferedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

                        // 轨道 (Track) - 最底层 (浅透明度)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(1.dp))
                        ) {
                            // 已缓存的进度 (Buffered Progress) - 中间层 (采用半透明白)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(bufferedFraction)
                                    .fillMaxHeight()
                                    .background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(1.dp))
                            )

                            // 已播放的进度 (Active Progress) - 最顶层 (主题粉/紫)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
                            )
                        }

                        // 极细进度条上的【极小滑点 (Thumb)】：随播放进度实时平移，美观无遮挡
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    val thumbSizePx = 8.dp.toPx()
                                    val maxOffsetPx = widthPx - thumbSizePx
                                    translationX = fraction * maxOffsetPx
                                }
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 进度条末尾：“已播放时间 / 总时长”文本配置
                    Text(
                        text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 右下角：全屏切换图标按键（绑定对应的全屏点击事件）
                    IconButton(
                        onClick = onFullscreenClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "全屏",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

// MediaPlayer (系统原生 VideoView 版)：极轻，没有任何外部第三方播放器依赖
@Composable
fun NativeMediaPlayer(videoUrl: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            android.widget.VideoView(ctx).apply {
                setVideoPath(videoUrl)
                val controller = android.widget.MediaController(ctx)
                controller.setAnchorView(this)
                setMediaController(controller) // 绑定最基础的原生 MediaController 条
                start() // 自动开始播放
            }
        },
        modifier = modifier
    )
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

// ==================== 播放时间数值格式化辅助函数 ====================

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}